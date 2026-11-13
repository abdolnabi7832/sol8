package com.institutional.forex.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "psychology_logs")
public class PsychologyLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_id", nullable = false, unique = true, length = 64)
    private String logId;

    @Column(name = "log_date", nullable = false, length = 16)
    private String logDate; // yyyy-MM-dd

    @Column(name = "mindset_score", nullable = false)
    private Integer mindsetScore = 8; // 1 to 10

    @Column(name = "fomo_level", length = 16)
    private String fomoLevel = "LOW"; // LOW, MED, HIGH

    @Column(name = "discipline_rating")
    private Integer disciplineRating = 5; // 1 to 5

    @Column(name = "sleep_hours")
    private Double sleepHours = 7.5;

    @Column(name = "emotional_state", length = 32)
    private String emotionalState = "CALM"; // CALM, CONFIDENT, STRESSED, EUPHORIC, FATIGUED

    @Column(name = "rules_followed")
    private Boolean rulesFollowed = true;

    @Lob
    @Column(name = "daily_notes", columnDefinition = "TEXT")
    private String dailyNotes;

    @Column(name = "recorded_at_utc", nullable = false)
    private LocalDateTime recordedAtUtc;

    public PsychologyLogEntity() {
        this.recordedAtUtc = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getLogId() { return logId; }
    public void setLogId(String logId) { this.logId = logId; }
    public String getLogDate() { return logDate; }
    public void setLogDate(String logDate) { this.logDate = logDate; }
    public Integer getMindsetScore() { return mindsetScore; }
    public void setMindsetScore(Integer mindsetScore) { this.mindsetScore = mindsetScore; }
    public String getFomoLevel() { return fomoLevel; }
    public void setFomoLevel(String fomoLevel) { this.fomoLevel = fomoLevel; }
    public Integer getDisciplineRating() { return disciplineRating; }
    public void setDisciplineRating(Integer disciplineRating) { this.disciplineRating = disciplineRating; }
    public Double getSleepHours() { return sleepHours; }
    public void setSleepHours(Double sleepHours) { this.sleepHours = sleepHours; }
    public String getEmotionalState() { return emotionalState; }
    public void setEmotionalState(String emotionalState) { this.emotionalState = emotionalState; }
    public Boolean getRulesFollowed() { return rulesFollowed; }
    public void setRulesFollowed(Boolean rulesFollowed) { this.rulesFollowed = rulesFollowed; }
    public String getDailyNotes() { return dailyNotes; }
    public void setDailyNotes(String dailyNotes) { this.dailyNotes = dailyNotes; }
    public LocalDateTime getRecordedAtUtc() { return recordedAtUtc; }
    public void setRecordedAtUtc(LocalDateTime recordedAtUtc) { this.recordedAtUtc = recordedAtUtc; }
}
