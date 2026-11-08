package com.riseup.domain;
import jakarta.persistence.*;
@Entity
@Table(name = "emergency_contacts")
public class EmergencyContactEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Integer id;
    @Column(name="user_id", nullable=false) private Integer userId;
    
    @Column(nullable=false) private String name;
    @Column(nullable=false) private String phone;
    @Column(name="consent_status") private String consentStatus = "pending";
    @Column(name="consent_token", nullable=false) private String consentToken;
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getPhone(){return phone;} public void setPhone(String v){phone=v;}
    public String getConsentStatus(){return consentStatus;} public void setConsentStatus(String v){consentStatus=v;}
    public String getConsentToken(){return consentToken;} public void setConsentToken(String v){consentToken=v;}

    public Integer getId(){return id;} public void setId(Integer id){this.id=id;}
    public Integer getUserId(){return userId;} public void setUserId(Integer userId){this.userId=userId;}

    public boolean hasConsent() {
        return consentStatus != null && "granted".equalsIgnoreCase(consentStatus);
    }
}
