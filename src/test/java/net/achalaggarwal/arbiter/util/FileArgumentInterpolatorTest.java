package net.achalaggarwal.arbiter.util;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static net.achalaggarwal.arbiter.util.FileArgumentInterpolator.interpolateFileVars;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

public class FileArgumentInterpolatorTest {

  @Test(expected = RuntimeException.class)
  public void shouldThrowExceptionIfCouldntResolveVariable() {
    interpolateFileVars(
      "somePath",
      new HashMap<String, List<String>>() { {
        put("vars", Arrays.asList("@@file@@"));
      } }
    ).get("vars");
  }

  @Test
  public void shouldReplaceVariableWithFileContents() throws IOException {
    final File tempFile = File.createTempFile("arbiter", "shouldReplaceVariableWithFileContents");
    FileUtils.writeStringToFile(tempFile, "file-content");
    List<String> strings = interpolateFileVars(
      tempFile.getParent(),
      new HashMap<String, List<String>>() { {
        put("vars", Arrays.asList("@@" + tempFile.getName() + "@@"));
      } }
    ).get("vars");

    assertThat(strings.get(0), is("file-content"));
  }

  @Test
  public void shouldReturnNullIfInputIsNull() throws IOException {
    assertNull(interpolateFileVars("somePath", null));
  }
}