package org.cbihi.mrinormalizer.application.port.out;

import org.cbihi.mrinormalizer.application.provenance.manifest.PublicJobReport;
import org.cbihi.mrinormalizer.domain.model.OutputTarget;

/** Exports only the supplied aggregate public projection to an explicit runtime target. */
public interface PublicReportWriter {
    void write(PublicJobReport report, OutputTarget target);
}
