package com.meridian.profile;

import com.meridian.profile.ProfileModels.Provenance;
import com.meridian.profile.ProfileModels.QuizAnswers;
import com.meridian.profile.ProfileModels.TravelProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "traveler_profiles")
public class TravelerProfileEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private TravelProfile profile;

    @JdbcTypeCode(SqlTypes.JSON)
    private QuizAnswers quiz;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Provenance provenance;

    @Column(nullable = false)
    private boolean edited;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TravelerProfileEntity() {}

    public TravelerProfileEntity(UUID userId) {
        this.userId = userId;
    }

    /** A fresh profile from the quiz replaces any earlier edits. */
    public void applyGenerated(TravelProfile profile, QuizAnswers quiz, Provenance provenance) {
        this.profile = profile;
        this.quiz = quiz;
        this.provenance = provenance;
        this.edited = false;
        this.updatedAt = Instant.now();
    }

    public void applyEdit(TravelProfile profile) {
        this.profile = profile;
        this.edited = true;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public TravelProfile getProfile() {
        return profile;
    }

    public QuizAnswers getQuiz() {
        return quiz;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public boolean isEdited() {
        return edited;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
