package org.bitbucket.ouyi;

import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by worker on 12/27/16.
 */
public class Integration {

    @Rule
    public final DropwizardAppRule<File2DbConfiguration> RULE = new DropwizardAppRule<>(
            File2DbApplication.class, ResourceHelpers.resourceFilePath("file2db.yml"));

    @Test
    public void testWithoutMessageQueue() throws IOException {
        final DBIFactory factory = new DBIFactory();
        final DBI dbi = factory.build(RULE.getEnvironment(), RULE.getConfiguration().getDataSourceFactory(), "h2");
        try (Handle handle = dbi.open()) {
            String createTableSql = "migrations/1-create-table-person.sql";
            handle.createStatement(createTableSql).execute();
        }
        Client client = new JerseyClientBuilder().build();
        String testFile = "test.csv";
        byte[] data = Files.readAllBytes(Paths.get(ResourceHelpers.resourceFilePath(testFile)));
        Response response;
        response = client.target(String.format("http://localhost:%d/upload", RULE.getLocalPort()))
                .path(testFile)
                .request()
                .put(Entity.entity(data, MediaType.APPLICATION_OCTET_STREAM));
        assertThat(response.getStatus()).isEqualTo(200);

        assertThat(Files.readAllBytes(Paths.get(RULE.getConfiguration().getStorageRoot(), testFile))).isEqualTo(data);

        response = client.target(String.format("http://localhost:%d/transform", RULE.getLocalPort()))
                .path(testFile)
                .request()
                .post(Entity.entity(null, MediaType.WILDCARD_TYPE));
        assertThat(response.getStatus()).isEqualTo(200);
        try (Handle handle = dbi.open()) {
            assertThat(handle.createQuery("select * from person").list().size()).isEqualTo(2);
        }
    }

}