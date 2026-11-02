package com.riseup.verification;

public class VerificationResponse {
    private String answer;
    private boolean completed;
    private String imageBase64;
    private boolean cameraError;

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
    public boolean isCameraError() { return cameraError; }
    public void setCameraError(boolean cameraError) { this.cameraError = cameraError; }
}
