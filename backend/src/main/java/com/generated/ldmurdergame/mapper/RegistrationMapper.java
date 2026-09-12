package com.generated.ldmurdergame.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.generated.ldmurdergame.model.SessionRegistration;

@Mapper
public interface RegistrationMapper {
  @Insert("INSERT INTO session_registrations (session_id, player_name, contact) "
      + "VALUES (#{sessionId}, #{playerName}, #{contact})")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(SessionRegistration registration);

  @Select("SELECT COUNT(*) FROM session_registrations WHERE session_id = #{sessionId}")
  int countBySession(@Param("sessionId") Long sessionId);

  @Select("SELECT * FROM session_registrations WHERE session_id = #{sessionId} AND player_name = #{playerName}")
  SessionRegistration findBySessionAndPlayer(@Param("sessionId") Long sessionId,
      @Param("playerName") String playerName);

  @Delete("DELETE FROM session_registrations WHERE session_id = #{sessionId} AND player_name = #{playerName}")
  int deleteBySessionAndPlayer(@Param("sessionId") Long sessionId, @Param("playerName") String playerName);
}
