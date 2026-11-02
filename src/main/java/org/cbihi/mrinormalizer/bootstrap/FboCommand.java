package org.cbihi.mrinormalizer.bootstrap;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.cbihi.mrinormalizer.application.fbo.FboBatchController;
import org.cbihi.mrinormalizer.application.fbo.FboRequest;
import org.cbihi.mrinormalizer.infrastructure.fbo.F9ForwardBatchRunner;
import org.cbihi.mrinormalizer.infrastructure.fbo.FboDirectoryPreflight;
import org.cbihi.mrinormalizer.infrastructure.fbo.FboReportWriter;

/** CLI entry point for an explicitly ordered forward series batch. */
public final class FboCommand {
    private FboCommand() {}
    public static void main(String[] args) { System.exit(run(args, System.out, System.err)); }
    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || out == null || err == null) return 2;
        FboRequest request;
        try {
            Map<String,String> options = new HashMap<>();
            List<FboRequest.SeriesSelection> selections = new ArrayList<>();
            for (int i = 0; i < args.length;) {
                String flag = args[i++];
                if (flag.equals("--series")) {
                    if (i + 1 >= args.length || selections.size() == 16) throw new IllegalArgumentException();
                    selections.add(new FboRequest.SeriesSelection(args[i++], args[i++]));
                } else if (flag.equals("--input-root") || flag.equals("--output-root")) {
                    if (i >= args.length || args[i].startsWith("--") || options.putIfAbsent(flag, args[i++]) != null)
                        throw new IllegalArgumentException();
                } else throw new IllegalArgumentException();
            }
            if (options.size() != 2) throw new IllegalArgumentException();
            request = new FboRequest(Path.of(options.get("--input-root")),
                    Path.of(options.get("--output-root")), selections);
        } catch (RuntimeException invalid) {
            err.println("FBO_INVALID_REQUEST");
            return 2;
        }
        try {
            var result = new FboBatchController(new FboDirectoryPreflight(),
                    new F9ForwardBatchRunner(), new FboReportWriter()).execute(request);
            // Public CLI output is opaque and contains no original source names, paths or UIDs.
            out.println("FBO_STATUS=" + result.status() + " BATCH_ID=" + result.batchId()
                    + " SELECTED=" + result.selectedCount() + " VERIFIED=" + result.verifiedCount()
                    + " FAILED=" + result.failedCount() + " NOT_ATTEMPTED=" + result.notAttemptedCount());
            return switch (result.status()) {
                case SUCCESS -> 0;
                case INVALID_REQUEST -> 2;
                case FAILED -> 3;
                case PARTIAL_SUCCESS -> 4;
                case RECOVERY_REQUIRED -> 5;
                case INTERRUPTED -> 130;
            };
        } catch (RuntimeException unknown) {
            err.println("FBO_RECOVERY_REQUIRED");
            return 5;
        }
    }
}
