package com.riseup.domain;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="checkins")
public class CheckInEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Integer id;
    @Column(name="alarm_id") private Integer alarmId;
    @Column(name="user_id", nullable=false) private Integer userId;
    @Column(name="scheduled_at", nullable=false) private LocalDateTime scheduledAt;
    @Column(name="sent_at") private LocalDateTime sentAt;
    @Column(name="responded_at") private LocalDateTime respondedAt;
    private String channel;
    private String status = "scheduled";
    @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
    public Integer getId(){return id;} public void setId(Integer id){this.id=id;}
    public Integer getAlarmId(){return alarmId;} public void setAlarmId(Integer alarmId){this.alarmId=alarmId;}
    public Integer getUserId(){return userId;} public void setUserId(Integer userId){this.userId=userId;}
    public LocalDateTime getScheduledAt(){return scheduledAt;} public void setScheduledAt(LocalDateTime scheduledAt){this.scheduledAt=scheduledAt;}
    public LocalDateTime getSentAt(){return sentAt;} public void setSentAt(LocalDateTime sentAt){this.sentAt=sentAt;}
    public LocalDateTime getRespondedAt(){return respondedAt;} public void setRespondedAt(LocalDateTime respondedAt){this.respondedAt=respondedAt;}
    public String getChannel(){return channel;} public void setChannel(String channel){this.channel=channel;}
    public String getStatus(){return status;} public void setStatus(String status){this.status=status;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime createdAt){this.createdAt=createdAt;}
}
