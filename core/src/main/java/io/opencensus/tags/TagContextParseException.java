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

package io.opencensus.tags;

/** Exception thrown when a {@link TagContext} cannot be parsed. */
public final class TagContextParseException extends Exception {
  private static final long serialVersionUID = 0L;

  /**
   * Constructs a new {@code TagContextParseException} with the given message.
   *
   * @param message a message describing the parse error.
   */
  public TagContextParseException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code TagContextParseException} with the given message and cause.
   *
   * @param message a message describing the parse error.
   * @param cause the cause of the parse error.
   */
  public TagContextParseException(String message, Throwable cause) {
    super(message, cause);
  }
}
