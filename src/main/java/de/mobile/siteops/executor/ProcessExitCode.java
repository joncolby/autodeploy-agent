package de.mobile.siteops.executor;

public enum ProcessExitCode {

    OK(0, "Ok"),
    SIGHUP(1, "Hangup detected on controlling terminal or death of controlling process"),
    SIGINT(2, "Interrupt from keyboard"),
    SIGQUIT(3, "Quit from keyboard"),
    SIGILL(4, "Illegal Instruction"),
    SIGABRT(6, "Abort signal from abort(3)"),
    SIGGFPE(8, "Floating point exception"),
    SIGKILL(9, "Kill signal"),
    SIGSERV(11, "Invalid memory reference"),
    SIGPIPE(13, "Broken pipe: write to pipe with no readers"),
    SIGALRM(14, "Timer signal from alarm(2)"),
    SIGTERM(15, "Termination signal"),
    UNKNOWN(65536, "Unknown exit code");
    
    private final int code;
    
    private final String description;
    
    private ProcessExitCode(int code, String description) {
        this.code = code;
        this.description = description;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getDescription() {
        return description;
    }
    
    public static ProcessExitCode getByCode(int code) {
        for (ProcessExitCode exitCode : values()) {
            if (exitCode.code == code) {
                return exitCode;
            }
        }
        return ProcessExitCode.UNKNOWN;
    }
    
}
