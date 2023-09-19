/*
 * PanelManager.java
 * Copyright (c) 2005 Torbjoern Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package org.xhtmlrenderer.demo.aboutbox;

import org.xhtmlrenderer.extend.UserAgentCallback;
import org.xhtmlrenderer.resource.CSSResource;
import org.xhtmlrenderer.resource.ImageResource;
import org.xhtmlrenderer.resource.XMLResource;
import org.xhtmlrenderer.swing.AWTFSImage;
import org.xhtmlrenderer.util.Uu;
import org.xhtmlrenderer.util.XRLog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * User: tobe
 * Date: 2005-jun-15
 * Time: 07:38:59
 */
public class DemoUserAgent implements UserAgentCallback {
    private String baseUrl;
    private int index = -1;
    private final List<String> history = new ArrayList<>();

    /**
     * an LRU cache
     */
    private final int imageCacheCapacity = 16;
    private final Map<String, ImageResource> imageCache =
            new LinkedHashMap<String, ImageResource>(imageCacheCapacity, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ImageResource> eldest) {
                    return size() > imageCacheCapacity;
                }
            };

    @Override
    public CSSResource getCSSResource(String uri) {
        InputStream is = null;
        uri = resolveURI(uri);
        try {
            URLConnection uc = new URL(uri).openConnection();
            uc.connect();
            is = uc.getInputStream();
        } catch (MalformedURLException e) {
            XRLog.exception("bad URL given: " + uri, e);
        } catch (IOException e) {
            XRLog.exception("IO problem for " + uri, e);
        }
        return new CSSResource(is);
    }

    @Override
    public ImageResource getImageResource(String uri) {
        uri = resolveURI(uri);
        ImageResource ir = imageCache.get(uri);
        //TODO: check that cached image is still valid
        if (ir == null) {
            InputStream is = null;
            try {
                URLConnection uc = new URL(uri).openConnection();
                uc.connect();
                is = uc.getInputStream();
            } catch (MalformedURLException e1) {
                XRLog.exception("bad URL given: " + uri, e1);
            } catch (IOException e11) {
                XRLog.exception("IO problem for " + uri, e11);
            }
            if (is != null) {
                try {
                    BufferedImage img = ImageIO.read(is);
                    ir = new ImageResource(uri, AWTFSImage.createImage(img));
                    imageCache.put(uri, ir);
                } catch (IOException e) {
                    XRLog.exception("Can't read image file; unexpected problem for URI '" + uri + "'", e);
                }
            }
        }
        if (ir == null) ir = new ImageResource(uri, null);
        return ir;
    }

    @Override
    public byte[] getBinaryResource(String uri) {
        InputStream is = null;
        try {
            URL url = new URL(uri);
            URLConnection conn = url.openConnection();
            is = conn.getInputStream();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buf = new byte[10240];
            int i;
            while ( (i = is.read(buf)) != -1) {
                result.write(buf, 0, i);
            }
            is.close();
            is = null;

            return result.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public XMLResource getXMLResource(String uri) {
        uri = resolveURI(uri);
        if (uri != null && uri.startsWith("file:")) {
            try {
                new File(new URI(uri));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
        XMLResource xr = null;
        InputStream inputStream = null;
        try {
            URLConnection uc = new URL(uri).openConnection();
            uc.connect();
            // TODO: String contentType = uc.getContentType(); Maybe should popup a choice when content/unknown!
            inputStream = uc.getInputStream();
            xr = XMLResource.load(inputStream);
        } catch (MalformedURLException e) {
            XRLog.exception("bad URL given: " + uri, e);
        } catch (IOException e) {
            XRLog.exception("IO problem for " + uri, e);
        } finally {
            if ( inputStream != null ) try {
                inputStream.close();
            } catch (IOException e) {
                // swallow
            }
        }
        if (xr == null) {
            String notFound = "<h1>Document not found</h1>";
            xr = XMLResource.load(new StringReader(notFound));
        }
        return xr;
    }

    @Override
    public boolean isVisited(String uri) {
        if (uri == null) return false;
        uri = resolveURI(uri);
        return history.contains(uri);
    }

    @Override
    public void setBaseURL(String url) {
        baseUrl = resolveURI(url);
        if (baseUrl == null) baseUrl = "error:FileNotFound";
        //setBaseURL is called by view when document is loaded
        if (index >= 0) {
            String historic = history.get(index);
            if (historic.equals(baseUrl)) return;//moved in history
        }
        index++;
        for (int i = index; i < history.size(); history.remove(i)) ;
        history.add(index, baseUrl);
    }

    @Override
    public String resolveURI(String uri) {
        URL ref = null;
        if (uri == null) return baseUrl;
        if (uri.trim().equals("")) return baseUrl;//jar URLs don't resolve this right
        if (uri.startsWith("demo:")) {
            DemoMarker marker = new DemoMarker();
            String short_url = uri.substring(5);
            if (!short_url.startsWith("/")) {
                short_url = "/" + short_url;
            }
            ref = marker.getClass().getResource(short_url);
            Uu.p("ref = " + ref);
        } else {
            try {
                URL base;
                if (baseUrl == null || baseUrl.length() == 0) {
                    ref = new URL(uri);
                } else {
                    base = new URL(baseUrl);
                    ref = new URL(base, uri);
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        if (ref == null)
            return null;
        else
            return ref.toExternalForm();
    }

    @Override
    public String getBaseURL() {
        return baseUrl;
    }


    public String getForward() {
        index++;
        return history.get(index);
    }

    public String getBack() {
        index--;
        return history.get(index);
    }

    public boolean hasForward() {
        return index + 1 < history.size() && index >= 0;
    }

    public boolean hasBack() {
        return index >= 0;
    }
}
