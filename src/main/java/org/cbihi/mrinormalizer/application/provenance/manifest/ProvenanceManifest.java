package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/** Immutable execution-evidence plan, composed by later owners from layout and supplied digests. */
public record ProvenanceManifest(
        int schemaVersion, UUID jobId, Instant createdAt,
        List<SourceFileRecord> sources, List<ManifestOperation> operations
) {
    public ProvenanceManifest {
        if (jobId == null || createdAt == null || sources == null || operations == null) {
            throw new IllegalArgumentException("Manifest components must be non-null");
        }
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Manifest schema version is unsupported");
        }
        if (sources.size() > 100000 || operations.size() > 100000) {
            throw new IllegalArgumentException("Manifest count limit exceeded");
        }
        var bySource = new HashMap<RelativePath, SourceFileRecord>();
        var orderedSources = new ArrayList<SourceFileRecord>(sources.size());
        for (var source : sources) {
            if (source == null || bySource.putIfAbsent(source.source(), source) != null) {
                throw new IllegalArgumentException("Manifest sources must be non-null and unique");
            }
            orderedSources.add(source);
        }
        var operationIds = new HashSet<String>();
        var orderedOperations = new ArrayList<ManifestOperation>(operations.size());
        var byDestination = new HashMap<RelativePath, ManifestOperation>();
        var destinations = new TreeSet<String>();
        long referenceCount = 0;
        for (var operation : operations) {
            if (operation == null || !operationIds.add(operation.operationId())) {
                throw new IllegalArgumentException("Manifest operations must be non-null and unique");
            }
            referenceCount += operation.sources().size();
            if (referenceCount > 200000) {
                throw new IllegalArgumentException("Manifest source reference limit exceeded");
            }
            for (var reference : operation.sources()) {
                if (!bySource.containsKey(reference)) {
                    throw new IllegalArgumentException("Operation references unknown source");
                }
            }
            var previous = byDestination.putIfAbsent(operation.destination(), operation);
            if (previous != null) {
                if (previous.kind() != ManifestOperation.Kind.COPY || operation.kind() != ManifestOperation.Kind.COPY) {
                    throw new IllegalArgumentException("Manifest destination mappings conflict");
                }
                var expected = bySource.get(previous.sources().get(0)).digest();
                var actual = bySource.get(operation.sources().get(0)).digest();
                if (expected.isEmpty() || actual.isEmpty() || !expected.equals(actual)) {
                    throw new IllegalArgumentException("Manifest destination mappings conflict");
                }
            }
            destinations.add(operation.destination().path());
            orderedOperations.add(operation);
        }
        // A planned file cannot also be the ancestor directory of another planned file.
        for (var destination : destinations) {
            var prefix = destination + "/";
            var candidate = destinations.ceiling(prefix);
            if (candidate != null && candidate.startsWith(prefix)) {
                throw new IllegalArgumentException("Manifest destination mappings conflict");
            }
        }
        orderedSources.sort((left, right) -> comparePathStrings(left.source().path(), right.source().path()));
        orderedOperations.sort(Comparator.comparing(ManifestOperation::operationId));
        sources = List.copyOf(orderedSources);
        operations = List.copyOf(orderedOperations);
    }

    private static int comparePathStrings(String left, String right) {
        int a = 0;
        int b = 0;
        while (a < left.length() && b < right.length()) {
            int x = left.codePointAt(a);
            int y = right.codePointAt(b);
            if (x != y) {
                return Integer.compare(x, y);
            }
            a += Character.charCount(x);
            b += Character.charCount(y);
        }
        return Integer.compare(left.length() - a, right.length() - b);
    }
}
