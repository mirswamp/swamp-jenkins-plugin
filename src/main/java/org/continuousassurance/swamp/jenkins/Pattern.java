
/* 
  SWAMP Jenkins Plugin

  Copyright 2016 Jared Sweetland, Vamshi Basupalli, James A. Kupsch

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express implied.
  See the License for the specific language governing permissions and
  limitations under the License.
  */

package org.continuousassurance.swamp.jenkins;

/**
 * Bug pattern describing a bug type.
 *
 * Derived from Ulli Hafner's Script
 */
public class Pattern {
    private String type;
    private String description;
    private String shortDescription;

    /**
     * Sets the type to the specified value.
     *
     * @param type
     *            the value to set
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the description to the specified value.
     *
     * @param description
     *            the value to set
     */
    public void setDescription(final String description) {
        this.description = description;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the shortDescription to the specified value.
     *
     * @param shortDescription
     *            the value to set
     */
    public void setShortDescription(final String shortDescription) {
        this.shortDescription = shortDescription;
    }

    /**
     * Returns the shortDescription.
     *
     * @return the shortDescription
     */
    public String getShortDescription() {
        return shortDescription;
    }
}
