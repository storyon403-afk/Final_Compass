package cn.finalscompass.service;

import cn.finalscompass.model.ApiModels.AnonymousProfile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AnonymousIdentityService {
    private static final List<String> WORDS = List.of("银杏", "松果", "青禾", "白露", "海盐", "星屿", "山岚", "小满");
    private final JdbcClient jdbc;

    public AnonymousIdentityService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional
    public AnonymousProfile forAccount(long appUserId) {
        String publicId = UUID.randomUUID().toString();
        String nickname = WORDS.get(ThreadLocalRandom.current().nextInt(WORDS.size())) + " " +
                String.format("%04d", ThreadLocalRandom.current().nextInt(10_000));
        jdbc.sql("INSERT IGNORE INTO anonymous_user(app_user_id, public_id, nickname) VALUES (:accountId, :publicId, :nickname)")
                .param("accountId", appUserId).param("publicId", publicId).param("nickname", nickname).update();
        return jdbc.sql("SELECT public_id, nickname FROM anonymous_user WHERE app_user_id = :accountId")
                .param("accountId", appUserId).query(AnonymousProfile.class).single();
    }

    public long internalIdForAccount(long appUserId) {
        forAccount(appUserId);
        return jdbc.sql("SELECT id FROM anonymous_user WHERE app_user_id = :accountId")
                .param("accountId", appUserId).query(Long.class).single();
    }
}
