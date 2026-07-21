package io.temporal.demos.replaytorepair.worker.triage;

import java.util.List;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TriageActivities {
    List<OwnerProfile> loadProfiles();

    String selectOwner(Issue issue, List<OwnerProfile> profiles);

    void notifyAssignment(Issue issue, String owner);
}
