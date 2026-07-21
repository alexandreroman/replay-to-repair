package io.temporal.demos.replaytorepair.worker.triage;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface TriageActivities {
    String selectOwner(Issue issue);

    void notifyAssignment(Issue issue, String owner);
}
