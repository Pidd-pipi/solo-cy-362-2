package com.generated.ldmurdergame.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Insert;
import com.generated.ldmurdergame.model.Script;

@Mapper
public interface ScriptMapper {
  @Insert("INSERT INTO scripts (name, genre, difficulty, duration_minutes, min_players, max_players, "
      + "dm_requirement, description, active) VALUES (#{name}, #{genre}, #{difficulty}, #{durationMinutes}, "
      + "#{minPlayers}, #{maxPlayers}, #{dmRequirement}, #{description}, TRUE)")
  @Options(useGeneratedKeys = true, keyProperty = "id")
  int insert(Script script);

  @Update("UPDATE scripts SET name = #{name}, genre = #{genre}, difficulty = #{difficulty}, "
      + "duration_minutes = #{durationMinutes}, min_players = #{minPlayers}, max_players = #{maxPlayers}, "
      + "dm_requirement = #{dmRequirement}, description = #{description} WHERE id = #{id}")
  int update(Script script);

  @Update("UPDATE scripts SET active = #{active} WHERE id = #{id}")
  int updateActive(@Param("id") Long id, @Param("active") boolean active);

  @Select("SELECT * FROM scripts WHERE id = #{id}")
  Script findById(@Param("id") Long id);

  @Select("<script>"
      + "SELECT * FROM scripts "
      + "<where>"
      + "  <if test='!includeInactive'> AND active = TRUE </if>"
      + "  <if test='name != null'> AND LOWER(name) LIKE LOWER(CONCAT('%', #{name}, '%')) </if>"
      + "  <if test='genre != null'> AND genre = #{genre} </if>"
      + "  <if test='difficulty != null'> AND difficulty = #{difficulty} </if>"
      + "</where>"
      + "ORDER BY active DESC, id DESC"
      + "</script>")
  List<Script> search(@Param("name") String name, @Param("genre") String genre,
      @Param("difficulty") String difficulty, @Param("includeInactive") boolean includeInactive);
}
