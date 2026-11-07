package org.cbihi.mrinormalizer.application.ebo;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable, ordinal-aligned results. NOT_ATTEMPTED entries are never counted as jobs. */
public record EboResult(UUID batchId, String inputMode, List<Entry> entries,
                        OverallStatus overallStatus, Optional<Path> reportPath) {
    public enum OverallStatus { SUCCESS, PARTIAL_SUCCESS, FAILED, RECOVERY_REQUIRED, INVALID_REQUEST, INTERRUPTED }
    public enum EntryState { SUCCESS, REJECTED, FAILED, RECOVERY_REQUIRED, NOT_ATTEMPTED }
    public record Entry(int ordinal, String input, UUID jobId, EntryState state, String m8Status,
                        String publication, int comparedSlices, String outputDirectory, List<String> faults) {
        public Entry {
            if(ordinal<1||input==null||input.isEmpty()||state==null||comparedSlices<0)
                throw new IllegalArgumentException("Invalid EBO entry");
            faults=List.copyOf(faults);
            if(state==EntryState.NOT_ATTEMPTED && (jobId!=null||m8Status!=null||publication!=null
                    ||outputDirectory!=null||comparedSlices!=0))
                throw new IllegalArgumentException("Unattempted input cannot have M8 results");
        }
    }
    public EboResult {
        Objects.requireNonNull(batchId); Objects.requireNonNull(inputMode);
        Objects.requireNonNull(overallStatus); Objects.requireNonNull(reportPath);
        entries=List.copyOf(entries);
        for(int i=0;i<entries.size();i++) if(entries.get(i).ordinal()!=i+1)
            throw new IllegalArgumentException("Entries must be ordinal-aligned");
    }
    public int discoveredCount(){return entries.size();}
    public int unattemptedCount(){return (int)entries.stream().filter(e->e.state()==EntryState.NOT_ATTEMPTED).count();}
    public int attemptedCount(){return discoveredCount()-unattemptedCount();}
    public int successCount(){return count(EntryState.SUCCESS);}
    public int rejectedCount(){return count(EntryState.REJECTED);}
    public int failedCount(){return count(EntryState.FAILED);}
    public int recoveryRequiredCount(){return count(EntryState.RECOVERY_REQUIRED);}
    private int count(EntryState state){return (int)entries.stream().filter(e->e.state()==state).count();}
    public EboResult withReportPath(Path path){return new EboResult(batchId,inputMode,entries,overallStatus,Optional.of(path));}
}
