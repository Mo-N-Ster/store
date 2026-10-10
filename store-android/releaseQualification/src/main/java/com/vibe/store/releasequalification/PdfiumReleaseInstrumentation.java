package com.vibe.store.releasequalification;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dependency-free instrumentation for the minified app's actual isolated PDFium service. */
public final class PdfiumReleaseInstrumentation extends Instrumentation {
    private static final String TAG = "PdfiumReleaseQual";
    private static final String SERVICE = "com.vibe.store.infrastructure.documents.parser.PdfSubjectIsolatedService";
    private static final String KEY_INPUT = "pdf_subject.read_only_fd";
    private static final String KEY_OUTPUT = "pdf_subject.utf16le_fd";
    private static final String KEY_LENGTH = "pdf_subject.length";
    private static final String EXPECTED = "STORE_DATA_V1:release-minified-subject";
    private final AtomicBoolean started = new AtomicBoolean(false);

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        Log.i(TAG, "onCreate invoked");
        startQualification();
    }

    @Override public void onStart() {
        Log.i(TAG, "onStart invoked");
        startQualification();
    }

    private void startQualification() {
        if (!started.compareAndSet(false, true)) return;
        // Never block the instrumentation/main looper: ServiceConnection and Messenger
        // callbacks need it to deliver the isolated-process handshake and result.
        new Thread(() -> {
            Bundle result = new Bundle();
            try {
                Log.i(TAG, "qualification worker started");
                runQualification();
                result.putString("stream", "PASS: minified Release service bound, JNI loaded, Subject matched");
                result.putInt("tests", 1);
                finish(0, result);
            } catch (Throwable failure) {
                Log.e(TAG, "qualification failed", failure);
                result.putString("stream", "FAIL: " + failure.getClass().getName() + ": " + failure.getMessage());
                finish(1, result);
            }
        }, "pdfium-release-qualification").start();
    }

    private void runQualification() throws Exception {
        Context target = getTargetContext();
        require("com.vibe.store".equals(target.getPackageName()), "wrong tested app " + target.getPackageName());
        CountDownLatch connected = new CountDownLatch(1);
        LinkedBlockingQueue<Messenger> services = new LinkedBlockingQueue<>(1);
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                services.offer(new Messenger(binder));
                connected.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        Intent intent = new Intent().setComponent(new ComponentName(target.getPackageName(), SERVICE));
        require(target.bindService(intent, connection, Context.BIND_AUTO_CREATE), "Release service bind rejected");
        try {
            require(connected.await(5, TimeUnit.SECONDS), "isolated service startup timeout");
            Messenger remote = services.poll(1, TimeUnit.SECONDS);
            require(remote != null, "service returned no Messenger");
            LinkedBlockingQueue<Message> replies = new LinkedBlockingQueue<>(1);
            Messenger reply = new Messenger(new Handler(Looper.getMainLooper(), message -> {
                replies.offer(Message.obtain(message));
                return true;
            }));

            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            try (ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                output.write(pdf(EXPECTED));
            }
            Message request = Message.obtain(null, 1, 19, 0);
            request.replyTo = reply;
            Bundle requestData = new Bundle();
            requestData.putParcelable(KEY_INPUT, pipe[0]);
            request.setData(requestData);
            remote.send(request);

            Message response = replies.poll(8, TimeUnit.SECONDS);
            require(response != null, "isolated parser response timeout");
            require(response.what == 2, "unexpected response type " + response.what);
            require(response.arg1 == 0, "PDFium extraction failed: " + response.arg1);
            ParcelFileDescriptor subjectFd = response.getData().getParcelable(KEY_OUTPUT);
            require(subjectFd != null, "no Subject pipe returned");
            int length = response.getData().getInt(KEY_LENGTH);
            require(length > 0 && length <= 5_333_364, "Subject length outside bound: " + length);
            byte[] actual = new byte[length];
            try (ParcelFileDescriptor.AutoCloseInputStream input = new ParcelFileDescriptor.AutoCloseInputStream(subjectFd)) {
                int offset = 0;
                while (offset < actual.length) {
                    int count = input.read(actual, offset, actual.length - offset);
                    require(count >= 0, "truncated Subject pipe");
                    offset += count;
                }
            }
            require(Arrays.equals(EXPECTED.getBytes(StandardCharsets.UTF_16LE), actual), "Subject bytes differ");
        } finally {
            target.unbindService(connection);
        }
    }

    private static byte[] pdf(String subject) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        String[] objects = {
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [] /Count 0 >>",
            "<< /Subject (" + subject + ") >>"
        };
        for (int i = 0; i < objects.length; i++) {
            offsets.add(out.size());
            out.write(((i + 1) + " 0 obj\n" + objects[i] + "\nendobj\n").getBytes(StandardCharsets.US_ASCII));
        }
        int xref = out.size();
        out.write("xref\n0 4\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i < offsets.size(); i++) {
            out.write(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)).getBytes(StandardCharsets.US_ASCII));
        }
        out.write(("trailer\n<< /Size 4 /Root 1 0 R /Info 3 0 R >>\nstartxref\n" + xref + "\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
