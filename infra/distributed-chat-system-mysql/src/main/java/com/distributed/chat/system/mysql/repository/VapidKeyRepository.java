package com.distributed.chat.system.mysql.repository;

import com.distributed.chat.system.mysql.entity.VapidKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface VapidKeyRepository extends JpaRepository<VapidKey, Integer> {

    /**
     * id = 1 행이 없을 때만 넣는다. 여러 인스턴스가 동시에 처음 떠도 하나만 들어가고 나머지는 무시된다
     *
     * @return 1 이면 내가 넣음, 0 이면 이미 있었음 (다른 인스턴스가 먼저 넣음)
     */
    @Transactional
    @Modifying
    @Query(value = """
        insert ignore into vapid_key (id, public_key, private_key)
        values (:id, :publicKey, :privateKey)
        """, nativeQuery = true)
    int insertIfAbsent(@Param("id") int id, @Param("publicKey") String publicKey, @Param("privateKey") String privateKey);
}
