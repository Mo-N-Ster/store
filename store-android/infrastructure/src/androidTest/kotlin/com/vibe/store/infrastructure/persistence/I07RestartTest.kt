package com.vibe.store.infrastructure.persistence

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vibe.store.api.*
import com.vibe.store.application.catalog.CatalogAuthority
import com.vibe.store.application.inventory.*
import com.vibe.store.application.persistence.CommandCoordinator
import com.vibe.store.application.purchases.*
import com.vibe.store.application.security.IdentityAuthority
import com.vibe.store.infrastructure.media.AndroidProductMedia
import com.vibe.store.infrastructure.security.BcryptPasswords
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * I07-P6 real process-death qualification.
 *
 * The prepare phase MUST be terminated externally after READY. A normal return
 * is always a test failure. The verify phase opens the same durable database in
 * a different PID and checks that no purchase, inventory or compensation effect
 * is invented or applied twice.
 */
class I07RestartTest {
    @Test
    fun realDeathPreservesPurchaseInventoryAndCompensationAtomicity() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("i07Phase")
        val operation = args.getString("i07Operation")
        val boundary = args.getString("i07Boundary")
        val run = args.getString("i07Run")

        require(
            phase in setOf("prepare", "verify") &&
                operation in setOf("purchase", "inventory", "compensation") &&
                boundary in setOf("before", "after") &&
                run != null &&
                run.matches(Regex("[A-Za-z0-9_-]{1,64}"))
        ) {
            "Explicit i07Phase/i07Operation/i07Boundary/i07Run required"
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPackage = InstrumentationRegistry.getInstrumentation().context.packageName
        val root = File(
            context.noBackupFilesDir,
            "i07-p6-$run-$operation-$boundary",
        )

        if (phase == "prepare") {
            require(!root.exists()) {
                "Synthetic I07-P6 run already exists; use a fresh i07Run"
            }
        }

        val owner = RoomDatabaseOwner(context, File(root, "database"))
        val commands = CommandCoordinator(owner)
        val identity = IdentityAuthority(owner, commands, BcryptPasswords())
        val media = AndroidProductMedia.fixture(File(root, "media")) {
            byteArrayOf().inputStream()
        }
        val catalog = CatalogAuthority(identity, media)

        var armed = false
        var targetId = 0L
        var productId = 0L
        var reviewToken = ""

        val marker = File(root, "ready")

        suspend fun awaitExternalDeath() {
            val payload = listOf(
                Process.myPid().toString(),
                testPackage,
                operation,
                boundary,
                run,
                targetId.toString(),
                productId.toString(),
                reviewToken.ifBlank { "-" },
            ).joinToString("\n")

            FileOutputStream(marker).use { output ->
                output.write(payload.toByteArray())
                output.fd.sync()
            }

            Log.i(
                "STORE_I07_P6",
                "READY run=$run operation=$operation boundary=$boundary " +
                    "pid=${Process.myPid()} testPackage=$testPackage",
            )

            awaitCancellation()
        }

        val purchaseProbe = PurchaseProbe { reached ->
            val expected = if (boundary == "before") {
                PurchaseBoundary.BEFORE_COMMIT
            } else {
                PurchaseBoundary.AFTER_COMMIT
            }

            if (
                phase == "prepare" &&
                armed &&
                operation in setOf("purchase", "compensation") &&
                reached == expected
            ) {
                awaitExternalDeath()
            }
        }

        val inventoryProbe = InventoryProbe { reached ->
            val expected = if (boundary == "before") {
                InventoryBoundary.BEFORE_COMMIT
            } else {
                InventoryBoundary.AFTER_COMMIT
            }

            if (
                phase == "prepare" &&
                armed &&
                operation == "inventory" &&
                reached == expected
            ) {
                awaitExternalDeath()
            }
        }

        val purchases = PurchaseAuthority(
            identity = identity,
            catalogService = catalog,
            probe = purchaseProbe,
        )
        val inventories = InventoryAuthority(
            identity = identity,
            probe = inventoryProbe,
        )

        suspend fun expectConflict(block: suspend () -> Unit) {
            try {
                block()
                fail("Expected WorkflowError.CONFLICT")
            } catch (failure: WorkflowFailure) {
                assertEquals(WorkflowError.CONFLICT, failure.code)
            }
        }

        try {
            if (phase == "prepare") {
                identity.bootstrap(
                    OwnerRegistration(
                        "owner",
                        "owner@example.invalid",
                        "Synthetic",
                        "Owner",
                        "Password-123",
                        "Question",
                        "Answer",
                    )
                )
                identity.login(Credentials("owner", "Password-123"))

                val product = catalog.save(
                    ProductDraft(
                        name = "I07 P6 $operation",
                        category = "Synthetic",
                        price = 3.0,
                        initialStock = 5.0,
                    )
                ).product

                productId = product.id

                when (operation) {
                    "purchase" -> {
                        val draft = purchases.createDraft(
                            CreatePurchaseDraft(idempotencyKey = "p6-$run")
                        )
                        purchases.saveLine(
                            SavePurchaseLine(
                                purchaseId = draft.summary.id,
                                productId = product.id,
                                quantity = 3,
                                unitCost = 2.0,
                            )
                        )

                        targetId = draft.summary.id
                        armed = true
                        purchases.validate(ValidatePurchase(targetId))
                    }

                    "compensation" -> {
                        val draft = purchases.createDraft(
                            CreatePurchaseDraft(idempotencyKey = "p6-$run")
                        )
                        purchases.saveLine(
                            SavePurchaseLine(
                                purchaseId = draft.summary.id,
                                productId = product.id,
                                quantity = 3,
                                unitCost = 2.0,
                            )
                        )
                        purchases.validate(ValidatePurchase(draft.summary.id))

                        targetId = draft.summary.id
                        armed = true
                        purchases.cancel(
                            CancelPurchase(targetId, "P6 compensation")
                        )
                    }

                    "inventory" -> {
                        val draft = inventories.start(
                            StartInventory("P6 physical count")
                        )
                        targetId = draft.id

                        val original = inventories.lines(targetId).items.single()
                        inventories.recordCount(
                            RecordInventoryCount(
                                inventoryId = targetId,
                                lineId = original.id,
                                productId = original.productId,
                                countedQuantity = 7,
                                expectedPreviousCount = original.countedQuantity,
                            )
                        )

                        val review = inventories.review(targetId)
                        reviewToken = review.token

                        val attestations = inventories.lines(targetId).items.map {
                            InventoryCountAttestation(
                                lineId = it.id,
                                productId = it.productId,
                                countedQuantity = it.countedQuantity,
                            )
                        }

                        armed = true
                        inventories.validate(
                            ValidateInventory(
                                inventoryId = targetId,
                                reviewToken = review.token,
                                physicallyConfirmed = true,
                                counts = attestations,
                            )
                        )
                    }
                }

                fail("External process death required after READY marker")
            } else {
                assertTrue("READY marker missing", marker.isFile)

                val evidence = marker.readLines()
                assertTrue("Malformed READY marker", evidence.size >= 8)

                val previousPid = evidence[0].toInt()
                val previousPackage = evidence[1]
                val previousOperation = evidence[2]
                val previousBoundary = evidence[3]
                val previousRun = evidence[4]
                val persistedTargetId = evidence[5].toLong()
                val persistedProductId = evidence[6].toLong()
                val previousReviewToken =
                    evidence[7].takeUnless { it == "-" }.orEmpty()

                assertNotEquals(previousPid, Process.myPid())
                assertEquals(testPackage, previousPackage)
                assertEquals(operation, previousOperation)
                assertEquals(boundary, previousBoundary)
                assertEquals(run, previousRun)

                assertNull("Session must not survive process death", identity.current())
                identity.login(Credentials("owner", "Password-123"))

                fun movementCount(reason: String): Int =
                    runBlocking {
                        catalog.movements().count { it.reason == reason }
                    }

                when (operation) {
                    "purchase" -> {
                        val detail = purchases.detail(persistedTargetId)

                        if (boundary == "before") {
                            assertEquals(PurchaseStatus.DRAFT, detail.summary.status)
                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(0, movementCount("purchase"))

                            purchases.validate(
                                ValidatePurchase(persistedTargetId)
                            )

                            assertEquals(
                                PurchaseStatus.VALIDATED,
                                purchases.detail(persistedTargetId).summary.status,
                            )
                            assertEquals(8L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("purchase"))
                        } else {
                            assertEquals(PurchaseStatus.VALIDATED, detail.summary.status)
                            assertEquals(8L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("purchase"))

                            purchases.validate(
                                ValidatePurchase(persistedTargetId)
                            )

                            assertEquals(8L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("purchase"))
                        }
                    }

                    "compensation" -> {
                        val detail = purchases.detail(persistedTargetId)

                        if (boundary == "before") {
                            assertEquals(PurchaseStatus.VALIDATED, detail.summary.status)
                            assertEquals(8L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("purchase"))
                            assertEquals(
                                0,
                                movementCount("purchase_cancellation"),
                            )

                            purchases.cancel(
                                CancelPurchase(
                                    persistedTargetId,
                                    "P6 compensation",
                                )
                            )

                            assertEquals(
                                PurchaseStatus.CANCELLED,
                                purchases.detail(persistedTargetId).summary.status,
                            )
                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(
                                1,
                                movementCount("purchase_cancellation"),
                            )
                        } else {
                            assertEquals(PurchaseStatus.CANCELLED, detail.summary.status)
                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("purchase"))
                            assertEquals(
                                1,
                                movementCount("purchase_cancellation"),
                            )

                            purchases.cancel(
                                CancelPurchase(
                                    persistedTargetId,
                                    "P6 compensation",
                                )
                            )

                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(
                                1,
                                movementCount("purchase_cancellation"),
                            )
                        }
                    }

                    "inventory" -> {
                        val detail = inventories.detail(persistedTargetId)

                        if (boundary == "before") {
                            assertEquals(InventoryStatus.DRAFT, detail.status)
                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(0, movementCount("inventory"))

                            val durable = inventories.lines(persistedTargetId).items
                            val staleAttestation = durable.map {
                                InventoryCountAttestation(
                                    lineId = it.id,
                                    productId = it.productId,
                                    countedQuantity = it.countedQuantity,
                                )
                            }

                            expectConflict {
                                inventories.validate(
                                    ValidateInventory(
                                        inventoryId = persistedTargetId,
                                        reviewToken = previousReviewToken,
                                        physicallyConfirmed = true,
                                        counts = staleAttestation,
                                    )
                                )
                            }

                            assertEquals(
                                InventoryStatus.DRAFT,
                                inventories.detail(persistedTargetId).status,
                            )
                            assertEquals(5L, catalog.detail(persistedProductId).stock)
                            assertEquals(0, movementCount("inventory"))

                            val freshReview = inventories.review(persistedTargetId)
                            val freshAttestation =
                                inventories.lines(persistedTargetId).items.map {
                                    InventoryCountAttestation(
                                        lineId = it.id,
                                        productId = it.productId,
                                        countedQuantity = it.countedQuantity,
                                    )
                                }

                            inventories.validate(
                                ValidateInventory(
                                    inventoryId = persistedTargetId,
                                    reviewToken = freshReview.token,
                                    physicallyConfirmed = true,
                                    counts = freshAttestation,
                                )
                            )

                            assertEquals(
                                InventoryStatus.VALIDATED,
                                inventories.detail(persistedTargetId).status,
                            )
                            assertEquals(7L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("inventory"))
                        } else {
                            assertEquals(InventoryStatus.VALIDATED, detail.status)
                            assertEquals(7L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("inventory"))

                            expectConflict {
                                inventories.validate(
                                    ValidateInventory(
                                        inventoryId = persistedTargetId,
                                        reviewToken = null,
                                        physicallyConfirmed = true,
                                        counts = emptyList(),
                                    )
                                )
                            }

                            assertEquals(7L, catalog.detail(persistedProductId).stock)
                            assertEquals(1, movementCount("inventory"))
                        }
                    }
                }

                assertTrue(
                    owner.verifyIntegrity().let {
                        it.quick && it.full && it.foreignKeys
                    }
                )

                println(
                    "I07 P6 VERIFIED operation=$operation boundary=$boundary " +
                        "oldPid=$previousPid newPid=${Process.myPid()}"
                )
            }
        } finally {
            owner.close()
        }
    }
}
