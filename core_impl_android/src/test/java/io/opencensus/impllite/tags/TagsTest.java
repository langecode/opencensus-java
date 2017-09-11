/*
 * Copyright 2017, OpenCensus Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opencensus.impllite.tags;

import static com.google.common.truth.Truth.assertThat;

import io.opencensus.implcore.tags.TagPropagationComponentImpl;
import io.opencensus.implcore.tags.TaggerImpl;
import io.opencensus.tags.Tags;
import io.opencensus.tags.TagsComponent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for accessing the {@link TagsComponent} through the {@link Tags} class. */
@RunWith(JUnit4.class)
public final class TagsTest {
  @Test
  public void getTagger() {
    assertThat(Tags.getTagger()).isInstanceOf(TaggerImpl.class);
  }

  @Test
  public void getTagContextSerializer() {
    assertThat(Tags.getTagPropagationComponent())
        .isInstanceOf(TagPropagationComponentImpl.class);
  }
}
