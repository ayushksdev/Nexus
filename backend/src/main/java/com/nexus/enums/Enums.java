package com.nexus.enums;

public interface Enums {
    enum WorkStatus {
        PENDING, PROCESSING, SUCCESS, FAILED
    }

    enum WorkAttemptStatus {
        STARTED, SUCCESS, FAILED
    }

    enum WorkerStatus {
        STARTING, RUNNING, FAILED, RESTARTING, OUT_OF_SERVICE, STOPPED
    }

    enum WorkerFailureMode {
        NORMAL, SLOW, CRASH, CRASH_ON_START
    }

    enum ReleaseStatus {
        PREPARING, DEPLOYING, WATCHING, SUCCESS, FAILED, ROLLED_BACK
    }
}
