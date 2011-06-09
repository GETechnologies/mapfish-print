/*
 * Copyright (C) 2009  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.output;

import com.lowagie.text.DocumentException;
import org.mapfish.print.MapPrinter;
import org.mapfish.print.RenderingContext;
import org.mapfish.print.utils.PJsonObject;

import java.io.OutputStream;

/**
 * Interface for exporting the generated PDF from MapPrinter.
 * 
 * User: jeichar
 * Date: Oct 18, 2010
 * Time: 1:49:41 PM
 */
public interface OutputFormat {
    RenderingContext print(MapPrinter printer, PJsonObject jsonSpec, OutputStream out, String referer) throws DocumentException;
    String contentType();
    String fileSuffix();
}
