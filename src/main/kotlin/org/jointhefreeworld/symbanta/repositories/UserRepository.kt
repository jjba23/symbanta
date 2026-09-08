package org.jointhefreeworld.symbanta.repositories

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<AppUser, Long> {
    fun findByUsername(username: String?): AppUser?
}

@Entity
@Table(name = "app_users")
class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(unique = true, nullable = false)
    var username: String? = null

    @Column(nullable = false)
    var password: String? = null

    var role: String? = "ROLE_USER"
}
