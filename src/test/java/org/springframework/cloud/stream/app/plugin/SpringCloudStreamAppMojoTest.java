/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.stream.app.plugin;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Before;
import org.junit.Test;

import org.springframework.util.ReflectionUtils;

/**
 * @author Soby Chacko
 */
public class SpringCloudStreamAppMojoTest {

    private SpringCloudStreamAppMojo springCloudStreamAppMojo = new SpringCloudStreamAppMojo();

    private Class<? extends SpringCloudStreamAppMojo> mojoClazz = springCloudStreamAppMojo.getClass();

    @Before
    public void setup() throws Exception {

        Field applicationType = mojoClazz.getDeclaredField("applicationType");
        applicationType.setAccessible(true);
        ReflectionUtils.setField(applicationType, springCloudStreamAppMojo, "stream");

        Field generatedProjectVersion = mojoClazz.getDeclaredField("generatedProjectVersion");
        generatedProjectVersion.setAccessible(true);
        ReflectionUtils.setField(generatedProjectVersion, springCloudStreamAppMojo, "1.0.0.BUILD-SNAPSHOT");

        Field generatedApps = mojoClazz.getDeclaredField("generatedApps");
        generatedApps.setAccessible(true);
        Map<String, GeneratableApp> generatableApps = new HashMap<>();
        generatableApps.put("foo-source-kafka", new GeneratableApp());
        ReflectionUtils.setField(generatedApps, springCloudStreamAppMojo, generatableApps);

        org.springframework.cloud.stream.app.plugin.Dependency bom = new org.springframework.cloud.stream.app.plugin.Dependency();
        bom.setArtifactId("spring-cloud-stream-app-dependencies");
        bom.setGroupId("org.springframework.cloud.stream.app");
        bom.setVersion("1.0.0.BUILD-SNAPSHOT");
        bom.setName("scs-bom");

        Field bomField = mojoClazz.getDeclaredField("bom");
        bomField.setAccessible(true);
        ReflectionUtils.setField(bomField, springCloudStreamAppMojo, bom);
    }

    @Test
    public void testDefaultProjectCreationByPlugin() throws Exception {
        springCloudStreamAppMojo.execute();

        String tmpdir = System.getProperty("java.io.tmpdir");
        Stream<Path> pathStream =
                Files.find(Paths.get(tmpdir), 3, (path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

        Path path = pathStream.findFirst().get();
        System.out.println(path);
        assertNotNull(path);

        assertGeneratedPomXml(path);
    }

    @Test
    public void testProjectCreatedIntoGeneartedProjectHome() throws Exception {
        String projectHome = "/tmp/apps";
        Field generatedProjectHome = mojoClazz.getDeclaredField("generatedProjectHome");
        generatedProjectHome.setAccessible(true);
        ReflectionUtils.setField(generatedProjectHome, springCloudStreamAppMojo, projectHome);

        springCloudStreamAppMojo.execute();

        Stream<Path> pathStream =
                Files.find(Paths.get(projectHome), 3, (path, attr) -> String.valueOf(path).contains("foo-source-kafka"));

        Path path = pathStream.findFirst().get();
        System.out.println(path);
        assertNotNull(path);

        assertGeneratedPomXml(path);

        //cleanup
        File projectRoot = new File(projectHome);
        FileUtils.cleanDirectory(projectRoot);
        FileUtils.deleteDirectory(projectRoot);
    }

    private void assertGeneratedPomXml(Path path) throws Exception {
        File pomXml = new File(path.toFile(), "pom.xml");

        InputStream is = new FileInputStream(pomXml);
        final MavenXpp3Reader reader = new MavenXpp3Reader();

        Model pomModel;
        try {
            pomModel = reader.read(is);
        }
        catch (IOException | XmlPullParserException e) {
            throw new IllegalStateException(e);
        }

        List<org.apache.maven.model.Dependency> dependencies = pomModel.getDependencies();
        assertThat(dependencies.size(), equalTo(3));
        assertThat(dependencies.stream()
                .filter(d -> d.getArtifactId().equals("spring-cloud-starter-stream-source-foo")).count(), equalTo(1L));

        assertThat(dependencies.stream()
                .filter(d -> d.getArtifactId().equals("spring-cloud-stream-binder-kafka")).count(), equalTo(1L));

        assertThat(pomModel.getParent().getArtifactId(), equalTo("spring-boot-starter-parent"));

        assertThat(pomModel.getArtifactId(), equalTo("foo-source-kafka"));
        assertThat(pomModel.getGroupId(), equalTo("org.springframework.cloud.stream.app"));
        assertThat(pomModel.getName(), equalTo("foo-source-kafka"));
        assertThat(pomModel.getVersion(), equalTo("1.0.0.BUILD-SNAPSHOT"));
        assertThat(pomModel.getDescription(), equalTo("Spring Cloud Stream Foo Source Kafka Binder Application"));

        List<Plugin> plugins = pomModel.getBuild().getPlugins();

        assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("spring-boot-maven-plugin")).count(), equalTo(1L));

        assertThat(plugins.stream().filter(p -> p.getArtifactId().equals("docker-maven-plugin")).count(), equalTo(1L));

        DependencyManagement dependencyManagement = pomModel.getDependencyManagement();
        List<org.apache.maven.model.Dependency> dependencies1 = dependencyManagement.getDependencies();
        assertThat(dependencies1.stream().filter(d -> d.getArtifactId().equals("spring-cloud-stream-app-dependencies")).count(),
                equalTo(1L));

        assertThat(pomModel.getRepositories().size(), equalTo(2));
    }
}