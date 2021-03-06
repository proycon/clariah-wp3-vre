package nl.knaw.meertens.clariah.vre.switchboard.deployment;

public enum DeploymentStatus {

    FINISHED(200, true),

    RUNNING(202, true),

    NOT_FOUND(404, true),

    /**
     * TODO: Atm not implemented
     */
    STOPPED(0, false),

    /**
     * Status is ALREADY_RUNNING when requesting existing deployment
     */
    ALREADY_RUNNING(403, false),

    /**
     * Status is DEPLOYED during deployment request, RUNNING afterwards
     */
    DEPLOYED(200, false);

    private final int httpStatus;

    private final boolean pollable;

    DeploymentStatus(int httpStatus, boolean pollable) {
        this.httpStatus = httpStatus;
        this.pollable = pollable;
    }

    /**
     * Status that a deployed service can return
     * when polling for its status
     */
    public boolean isPollStatus() {
        return pollable;
    }

    /**
     * Status that the deployment service can return
     * when deploying a new status
     */
    public boolean isDeployStatus() {
        return !pollable;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Get status based on http status code of poll request
     */
    public static DeploymentStatus getPollStatus(int httpStatus) {
        for(DeploymentStatus deploymentStatus : DeploymentStatus.values()) {
            if(deploymentStatus.pollable && deploymentStatus.httpStatus == httpStatus) {
                return deploymentStatus;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Deployment status with http code [%d] does not exist",
                httpStatus
        ));
    }

    /**
     * Get status based on http status code of deploy request
     */
    public static DeploymentStatus getDeployStatus(int httpStatus) {
        for(DeploymentStatus deploymentStatus : DeploymentStatus.values()) {
            if(!deploymentStatus.pollable && deploymentStatus.httpStatus == httpStatus) {
                return deploymentStatus;
            }
        }
        throw new IllegalArgumentException(String.format(
                "Deployment status with http code [%d] does not exist",
                httpStatus
        ));
    }
}
