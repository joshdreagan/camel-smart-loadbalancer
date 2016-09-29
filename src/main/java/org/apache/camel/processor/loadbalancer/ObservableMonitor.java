/*
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
package org.apache.camel.processor.loadbalancer;

import java.util.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservableMonitor extends Observable implements Monitor {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  private Object value;

  @Override
  public Object getValue() {
    return value;
  }

  protected void setValue(Object value) {
    if (value != this.value) {
      log.debug(String.format("Updating value [%s] to [%s].", this.value, value));
      this.value = value;
      setChanged();
      notifyObservers(value);
    }
  }
}
