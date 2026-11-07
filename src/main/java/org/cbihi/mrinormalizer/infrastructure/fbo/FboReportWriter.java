package org.cbihi.mrinormalizer.infrastructure.fbo;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController.ReportSink;
import org.cbihi.mrinormalizer.application.fbo.FboResult;

/** Whitelisted UTF-8 technical report; no source files, DICOM UIDs, or exception text. */
public final class FboReportWriter implements ReportSink {
    private static String q(String s) { return "\"" + s + "\""; } // all inputs are closed ASCII enums/UUIDs
    public static String serialize(FboResult result) {
        // Controller passes a provisional PARTIAL_SUCCESS until no-replace publication completes;
        // an all-verified, no-uncertainty set is the SUCCESS candidate being committed.
        String terminal = result.status().name();
        if (result.status() == FboResult.Status.PARTIAL_SUCCESS
                && result.selectedCount() > 0 && result.verifiedCount() == result.selectedCount())
            terminal = FboResult.Status.SUCCESS.name();
        StringBuilder b = new StringBuilder(768);
        b.append('{').append(q("schemaVersion")).append(':').append(q("PAI-FBO-REPORT-1.0"))
                .append(',').append(q("batchId")).append(':').append(q(result.batchId().toString()))
                .append(',').append(q("selectedCount")).append(':').append(result.selectedCount())
                .append(',').append(q("attemptedCount")).append(':').append(result.attemptedCount())
                .append(',').append(q("verifiedCount")).append(':').append(result.verifiedCount())
                .append(',').append(q("failedCount")).append(':').append(result.failedCount())
                .append(',').append(q("recoveryRequiredCount")).append(':').append(result.recoveryRequiredCount())
                .append(',').append(q("notAttemptedCount")).append(':').append(result.notAttemptedCount())
                .append(',').append(q("terminalStatus")).append(':').append(q(terminal))
                .append(',').append(q("entries")).append(':').append('[');
        for (int i = 0; i < result.entries().size(); i++) {
            var entry = result.entries().get(i);
            if (i > 0) b.append(',');
            b.append('{').append(q("ordinal")).append(':').append(entry.ordinal())
                    .append(',').append(q("jobId")).append(':').append(entry.jobId() == null ? "null" : q(entry.jobId().toString()))
                    .append(',').append(q("state")).append(':').append(q(entry.state().name()))
                    .append(',').append(q("reason")).append(':').append(q(entry.reason().name()))
                    .append(',').append(q("outputDirectory")).append(':')
                    .append(entry.outputDirectory() == null ? "null" : q(entry.outputDirectory()))
                    .append(',').append(q("verifiedOutput")).append(':').append(entry.verifiedOutput())
                    .append('}');
        }
        return b.append("]}\n").toString();
    }
    @Override public Path publish(FboResult result, Path existingBatchOutputRoot) throws IOException {
        if (result == null || existingBatchOutputRoot == null) throw new IOException("Invalid report request");
        Path root = existingBatchOutputRoot.toRealPath();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
            throw new IOException("Invalid report root");
        Path finalName = root.resolve("fbo-" + result.batchId() + "-summary.json");
        byte[] encoded = serialize(result).getBytes(StandardCharsets.UTF_8);
        Path stage = Files.createTempFile(root, ".fbo-report-", ".tmp");
        try {
            try (FileChannel ch = FileChannel.open(stage, StandardOpenOption.WRITE)) {
                ByteBuffer data = ByteBuffer.wrap(encoded);
                while (data.hasRemaining()) ch.write(data);
                ch.force(true);
            }
            Files.createLink(finalName, stage); // exclusive no-replace on supported providers
            if (!Arrays.equals(encoded, Files.readAllBytes(finalName)))
                throw new IOException("Uncertain report publication");
        } finally {
            Files.deleteIfExists(stage); // never delete a published report, even on failure
        }
        return finalName;
    }
}
