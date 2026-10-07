# STORE 3.0 — I07-P6 : qualification finale

Date : 2026-10-07.

Statut : **PASS**.

Baseline : `bf2ddd419c654b4c3eda8b6e9017f6ee43035775`, branche `main`.

## P6-A — vraie mort de processus

Résultat final :

- purchase-before : PASS_FROM_R2F ;
- purchase-after : PASS ;
- inventory-before : PASS ;
- inventory-after : PASS ;
- compensation-before : PASS ;
- compensation-after : PASS ;
- vraie mort de processus : 6/6 ;
- vérifications : 6/6.

## P6-B — UI

- 599/600/839/840 dp : PASS ;
- portrait/paysage : PASS ;
- FR/EN : PASS ;
- light/dark : PASS ;
- IME/focus : PASS ;
- virgule/point : PASS ;
- scroll indépendant : PASS ;
- cibles >=48dp : PASS ;
- captures : 15/15 ;
- revue visuelle : PASS.

Bundle UI final SHA-256 :

`eef129f9acae2b30a21edcebb9a0561809b2431a38da1cc11dbf20bf92f5a960`.

## P6-C1 — gates communs

- architecture : PASS ;
- JVM : 45, failure 0, error 0, skipped 0 ;
- build final hors ligne : PASS ;
- lint Fatal/Error : 0 ;
- warnings lint : 16 non bloquants ;
- diff check : PASS.

## P6-C2 — natives explicites

- `I07PersistenceTest` : 8/8 ;
- `InventoryNativeTest` : 9/9 ;
- `PurchaseNativeTest` : 8/8 ;
- total : 25/25 ;
- FAIL : 0 ;
- requis SKIPPED : 0.

Bundle C2 SHA-256 :

`3ca060e01aabcf85a260eff20defacace8c2253bdb9a1e8bab15f0b7ade6bab6`.

## Conclusion

`I07-P6 = PASS`.
