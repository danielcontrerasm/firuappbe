package com.example.pettracker.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SearchGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pet_id")
    private Pet pet;

    private String groupName;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CLOSED = "CLOSED";

    /** PRIVATE preserves the original behavior; PUBLIC is discoverable and joinable by anyone. */
    @Builder.Default
    private String visibility = "PRIVATE";

    @Column(length = 1000)
    private String description;

    @Builder.Default
    private String status = STATUS_ACTIVE;

    private String area;

    private String city;

    private String leaderName;

    private String leaderPhone;

    private Double coverageRadiusKm;

    @ManyToOne
    @JoinColumn(name = "created_by")
    private User createdBy;

    private String imageContentType;

    private String imageFileName;

    @JsonIgnore
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(columnDefinition = "bytea")
    private byte[] imageData;

    @Column(length = 1000)
    private String whatsappGroupLink;

    @Builder.Default
    private Instant createdAt = Instant.now();

}
