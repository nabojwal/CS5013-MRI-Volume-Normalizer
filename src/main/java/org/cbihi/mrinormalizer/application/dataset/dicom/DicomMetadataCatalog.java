package org.cbihi.mrinormalizer.application.dataset.dicom;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Immutable restricted observations; coverage of an inventory is service-owned. */
public record DicomMetadataCatalog(List<DicomMetadataInspection> inspections) {
    public DicomMetadataCatalog {
        if (inspections == null) throw new IllegalArgumentException("Catalog requires unique non-null inspections");
        var sources = new HashSet<String>();
        for (var i : inspections) if (i == null || !sources.add(i.source().path())) {
            throw new IllegalArgumentException("Catalog requires unique non-null inspections");
        }
        var ordered = new ArrayList<>(inspections);
        ordered.sort(Comparator.comparing(i -> i.source().path(), DicomMetadataCatalog::compare));
        inspections = List.copyOf(ordered);
    }
    /** All contained inspections succeeded; never implies series validity or READY. */
    public boolean complete() { return inspections.stream().allMatch(i -> i.metadata().isPresent()); }
    private static int compare(String a, String b) {
        return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
