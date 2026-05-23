package com.anvistudio.boutique.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Date;

/**
 * Entity to store messages submitted via the Contact Us form.
 * Mapped to the 'contact_messages' table in the database.
 */
@Entity
@Table(name = "contact_queries")
@Data // Lombok annotation for getters, setters, toString, etc.
@NoArgsConstructor // Lombok for no-argument constructor
@AllArgsConstructor // Lombok for constructor with all arguments
public class ContactMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = true)
    private String phoneNumber;

    @Column(nullable = true)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateSubmitted = new Date();

    @PrePersist
    protected void onCreate() {
        if (this.dateSubmitted == null) {
            this.dateSubmitted = new Date();
        }
    }
}