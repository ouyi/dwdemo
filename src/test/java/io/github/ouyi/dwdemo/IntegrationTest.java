package io.github.ouyi.dwdemo;

import io.dropwizard.jdbi.DBIFactory;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.github.ouyi.dwdemo.mq.DemoWorker;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Note this requires a running rabbitmq instance.
 */
public class IntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationTest.class);

    @Rule
    public final DropwizardAppRule<DemoConfiguration> RULE = new DropwizardAppRule<>(
            DemoApplication.class, ResourceHelpers.resourceFilePath("dwdemo.yml"));

    @Test
    public void testWithMessageQueue() throws IOException, InterruptedException {
        final DBIFactory factory = new DBIFactory();
        final DBI dbi = factory.build(RULE.getEnvironment(), RULE.getConfiguration().getDataSourceFactory(), "h2");
        try (Handle handle = dbi.open()) {
            String createTableSql = "migrations/1-create-table-person.sql";
            handle.createStatement(createTableSql).execute();
            handle.createStatement("delete from person").execute();
        }

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DemoWorker.main(new String[]{"-c ", ResourceHelpers.resourceFilePath("worker.yml")});
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            }
        });

        worker.start();

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

        Thread.sleep(5000);
        try (Handle handle = dbi.open()) {
            assertThat(handle.createQuery("select * from person").list().size()).isEqualTo(2);
        }
    }

}
