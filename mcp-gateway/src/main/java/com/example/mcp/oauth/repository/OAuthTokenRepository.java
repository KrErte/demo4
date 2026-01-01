package com.example.mcp.oauth.repository;

import com.example.mcp.oauth.model.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, String> {

    Optional<OAuthToken> findByEmailAndProvider(String email, String provider);

    List<OAuthToken> findByEmail(String email);

    List<OAuthToken> findByProvider(String provider);

    boolean existsByEmailAndProvider(String email, String provider);

    void deleteByEmailAndProvider(String email, String provider);
}
