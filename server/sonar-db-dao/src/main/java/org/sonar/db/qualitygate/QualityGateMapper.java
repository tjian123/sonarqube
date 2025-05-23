/*
 * SonarQube
 * Copyright (C) 2009-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.db.qualitygate;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.ResultHandler;

public interface QualityGateMapper {

  void insertQualityGate(QualityGateDto qualityGate);

  List<QualityGateDto> selectAll();

  QualityGateDto selectByName(String name);

  List<QualityGateDto> selectByNames(@Param("names") Collection<String> names);

  List<QualityGateDto> selectBuiltIn();

  void delete(String uuid);

  void deleteByUuids(@Param("uuids") Collection<String> uuids);

  void update(QualityGateDto qGate);

  void ensureOnlySonarWayQualityGatesAreBuiltIn(@Param("names") Collection<String> sonarWayQualityGatesName);

  void selectQualityGateFindings(String qualityGateUuid, ResultHandler<QualityGateFindingDto> handler);

  QualityGateDto selectByUuid(String uuid);

  QualityGateDto selectDefault();

  QualityGateDto selectByProjectUuid(@Param("projectUuid") String projectUuid);

  long countByNameStarting(String name);

}
