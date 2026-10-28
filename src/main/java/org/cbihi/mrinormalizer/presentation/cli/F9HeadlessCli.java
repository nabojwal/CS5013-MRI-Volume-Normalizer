package org.cbihi.mrinormalizer.presentation.cli;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;
import org.cbihi.mrinormalizer.application.provenance.manifest.RelativePath;
import org.cbihi.mrinormalizer.application.request.F9HeadlessRequest;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult;
import org.cbihi.mrinormalizer.application.result.F9HeadlessResult.Reason;
import org.cbihi.mrinormalizer.infrastructure.headless.F9WindowsComposition;

/** Console-safe one selected-slab conversion; never prints source names or UIDs. */
public final class F9HeadlessCli {
    private F9HeadlessCli() {}
    public static void main(String[] args) { System.exit(run(args)); }

    public static int run(String[] args) {
        // Configuration failures are classified only before opening the execution session.
        // After a session exists, *any* execution/close failure is recovery-required.
        try {
            if (args == null || args.length != 10) return configError();
            var options = new HashMap<String,String>();
            for (int i=0;i<args.length;i+=2) {
                if (!Set.of("--source-root","--output-root","--series-uid","--destination","--job-id")
                        .contains(args[i]) || options.putIfAbsent(args[i],args[i+1]) != null)
                    return configError();
            }
            Path input = Path.of(options.get("--source-root")).toRealPath();
            Path output = Path.of(options.get("--output-root")).toRealPath();
            UUID job = UUID.fromString(options.get("--job-id"));
            var dest = new RelativePath(RelativePath.Root.OUTPUT,options.get("--destination"));
            if (input.equals(output) || input.startsWith(output) || output.startsWith(input)
                    || !Files.isDirectory(input) || !Files.isDirectory(output)) return configError();
            var selected = new ArrayList<RelativePath>();
            // Source root contains the explicitly prepared slab; nothing is silently excluded.
            try (var stream = Files.newDirectoryStream(input)) {
                for (Path path : stream) {
                    if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                            || Files.isSymbolicLink(path) || selected.size() == 128)
                        return configError();
                    selected.add(new RelativePath(RelativePath.Root.SOURCE,path.getFileName().toString()));
                }
            }
            selected.sort(Comparator.comparing(RelativePath::path));
            var request = new F9HeadlessRequest(job, options.get("--series-uid"), selected,dest);
            var service = F9WindowsComposition.open(input,output,job);
            return executeAndReport(() -> service.executeOne(request), service);
        } catch (IllegalArgumentException | IOException | SecurityException exception) {
            return configError();
        } catch (RuntimeException exception) {
            return emit(F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN, Optional.empty()));
        }
    }

    /** Injectable, package-private reporting seam; emission follows completion of close(). */
    static int executeAndReport(Supplier<F9HeadlessResult> operation, AutoCloseable session) {
        F9HeadlessResult result;
        try {
            result = Objects.requireNonNull(operation.get());
        } catch (RuntimeException exception) {
            result = F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN, Optional.empty());
        }
        try {
            session.close();
        } catch (Exception exception) {
            // Even a previously verified result cannot be emitted as success after failed cleanup.
            result = F9HeadlessResult.uncertain(Reason.PUBLICATION_UNCERTAIN,
                    result.acknowledgedHead());
        }
        return emit(result);
    }

    private static int emit(F9HeadlessResult result) {
        System.out.println("F9_STATUS=" + result.status());
        if (!result.reasons().isEmpty()) System.out.println("F9_REASON=" + result.reasons().getFirst());
        return switch (result.status()) {
            case VERIFIED_PUBLISHED -> 0;
            case FAILED -> 2;
            case RECOVERY_REQUIRED -> 3;
        };
    }

    private static int configError() {
        System.out.println("F9_STATUS=CONFIGURATION_ERROR");
        return 4;
    }
}
