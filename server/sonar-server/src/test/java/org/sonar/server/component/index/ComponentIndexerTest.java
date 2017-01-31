/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
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
package org.sonar.server.component.index;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.sonar.api.config.MapSettings;
import org.sonar.api.utils.System2;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.component.ComponentUpdateDto;
import org.sonar.db.organization.OrganizationDto;
import org.sonar.db.organization.OrganizationTesting;
import org.sonar.server.es.EsClient;
import org.sonar.server.es.EsTester;
import org.sonar.server.es.ProjectIndexer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.sonar.server.component.index.ComponentIndexDefinition.FIELD_NAME;
import static org.sonar.server.component.index.ComponentIndexDefinition.INDEX_COMPONENTS;
import static org.sonar.server.component.index.ComponentIndexDefinition.TYPE_COMPONENT;

public class ComponentIndexerTest {

  private System2 system2 = System2.INSTANCE;

  @Rule
  public EsTester esTester = new EsTester(new ComponentIndexDefinition(new MapSettings()));

  @Rule
  public DbTester dbTester = DbTester.create(system2);

  private DbClient dbClient = dbTester.getDbClient();
  private DbSession dbSession = dbTester.getSession();
  private OrganizationDto organization;

  @Before
  public void setUp() {
    organization = OrganizationTesting.newOrganizationDto();
  }

  @Test
  public void index_nothing() {
    index();
    assertThat(count()).isZero();
  }

  @Test
  public void index_everything() {
    insert(ComponentTesting.newProjectDto(organization));

    index();
    assertThat(count()).isEqualTo(1);
  }

  @Test
  public void index_unexisting_project_while_database_contains_another() {
    insert(ComponentTesting.newProjectDto(organization, "UUID-1"));

    index("UUID-2");
    assertThat(count()).isEqualTo(0);
  }

  @Test
  public void index_one_project() {
    ComponentDto project = ComponentTesting.newProjectDto(organization, "UUID-1");
    insert(project);

    index(project);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  public void index_one_project_containing_a_file() {
    ComponentDto projectComponent = ComponentTesting.newProjectDto(organization, "UUID-PROJECT-1");
    insert(projectComponent);
    insert(ComponentTesting.newFileDto(projectComponent));

    index(projectComponent);
    assertThat(count()).isEqualTo(2);
  }

  @Test
  public void index_and_update_and_reindex_project() {

    // insert
    ComponentDto component = ComponentTesting.newProjectDto(organization, "UUID-1").setName("OldName");
    insert(component);

    // verify insert
    index(component);
    assertMatches("OldName", 1);

    // modify
    component.setName("NewName");
    update(component);

    // verify modification
    index(component);
    assertMatches("OldName", 0);
    assertMatches("NewName", 1);
  }

  @Test
  public void index_and_update_and_reindex_project_with_files() {

    // insert
    ComponentDto project = dbTester.components().insertProject();
    ComponentDto file = dbTester.components().insertComponent(ComponentTesting.newFileDto(project).setName("OldFile"));

    // verify insert
    index(project);
    assertMatches("OldFile", 1);

    // modify
    file.setName("NewFile");
    update(file);

    // verify modification
    index(project);
    assertMatches("OldFile", 0);
    assertMatches("NewFile", 1);
  }

  @Test
  public void full_reindexing_on_empty_index() {

    // insert
    ComponentDto project = dbTester.components().insertProject();
    dbTester.components().insertComponent(ComponentTesting.newFileDto(project).setName("OldFile"));

    // verify insert
    index();
    assertMatches("OldFile", 1);
  }

  @Test
  public void full_reindexing_should_not_do_anything_if_index_is_not_empty() {
    EsClient esMock = mock(EsClient.class);

    // attempt to start indexing
    ComponentIndexer indexer = spy(new ComponentIndexer(dbClient, esMock));
    doReturn(false).when(indexer).isEmpty();
    indexer.index();

    // verify, that index has not been altered
    verify(indexer).index();
    verify(indexer).isEmpty();
    verifyNoMoreInteractions(indexer);
    verifyNoMoreInteractions(esMock);
  }

  @Test
  public void isEmpty_should_return_true_if_index_is_empty() {
    assertThat(createIndexer().isEmpty()).isTrue();
  }

  @Test
  public void isEmpty_should_return_false_if_index_is_not_empty() {
    index_one_project();
    assertThat(createIndexer().isEmpty()).isFalse();
  }

  private void insert(ComponentDto component) {
    dbTester.components().insertComponent(component);
  }

  private void update(ComponentDto component) {
    ComponentUpdateDto updateComponent = ComponentUpdateDto.copyFrom(component);
    updateComponent.setBChanged(true);
    dbClient.componentDao().update(dbSession, updateComponent);
    dbClient.componentDao().applyBChangesForRootComponentUuid(dbSession, component.getRootUuid());
    dbSession.commit();
  }

  private void index() {
    createIndexer().index();
  }

  private void index(ComponentDto component) {
    index(component.uuid());
  }

  private void index(String uuid) {
    createIndexer().indexProject(uuid, ProjectIndexer.Cause.PROJECT_CREATION);
  }

  private long count() {
    return esTester.countDocuments(INDEX_COMPONENTS, TYPE_COMPONENT);
  }

  private void assertMatches(String nameQuery, int numberOfMatches) {
    assertThat(
      esTester.client()
        .prepareSearch(INDEX_COMPONENTS)
        .setTypes(TYPE_COMPONENT)
        .setQuery(termQuery(FIELD_NAME, nameQuery))
        .get()
        .getHits()
        .getTotalHits()).isEqualTo(numberOfMatches);
  }

  private ComponentIndexer createIndexer() {
    return new ComponentIndexer(dbTester.getDbClient(), esTester.client());
  }

}