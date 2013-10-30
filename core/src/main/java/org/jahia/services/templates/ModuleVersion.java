/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2014 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.templates;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents version of a module.
 */
public class ModuleVersion implements Comparable<ModuleVersion> {
    
    private static final Pattern VERSION_PATTERN = Pattern.compile("[^0-9]+");

    private boolean isSnapshot;

    private List<Integer> orderedVersionNumbers = new ArrayList<Integer>();
    
    private String versionString;

    /**
     * Initializes an instance of this class.
     * @param versionString the plain text representation of the module version
     */
    public ModuleVersion(String versionString) {
        this.versionString = versionString;
        if (versionString.endsWith("SNAPSHOT")) {
            isSnapshot = true;
        }
        String[] numbers = VERSION_PATTERN.split(versionString);
        for (String number : numbers) {
            orderedVersionNumbers.add(Integer.parseInt(number));
        }
    }

    @Override
    public int compareTo(ModuleVersion o) {
        for (int i = 0; i < Math.min(orderedVersionNumbers.size(), o.orderedVersionNumbers.size()); i++) {
            int c = orderedVersionNumbers.get(i).compareTo(o.getOrderedVersionNumbers().get(i));
            if (c != 0) {
                return c;
            }
        }
        int c = Integer.valueOf(orderedVersionNumbers.size()).compareTo(o.orderedVersionNumbers.size());
        if (c != 0) {
            return c;
        }
        return versionString.compareTo(o.versionString);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModuleVersion that = (ModuleVersion) o;

        if (versionString != null ? !versionString.equals(that.versionString) : that.versionString != null) {
            return false;
        }

        return true;
    }

    /**
     * Returns a list of ordered version numbers.
     * @return a list of ordered version numbers
     */
    public List<Integer> getOrderedVersionNumbers() {
        return orderedVersionNumbers;
    }

    @Override
    public int hashCode() {
        return versionString != null ? versionString.hashCode() : 0;
    }

    /**
     * Checks if the current version is a SNAPSHOT or not.
     * @return <code>true</code> if the current version is a SNAPSHOT; <code>false</code> otherwise
     */
    public boolean isSnapshot() {
        return isSnapshot;
    }

    @Override
    public String toString() {
        return versionString;
    }
}