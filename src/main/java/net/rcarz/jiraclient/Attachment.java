/**
 * jira-client - a simple JIRA REST client
 * Copyright (c) 2013 Bob Carroll (bob.carroll@alum.rit.edu)
 * <p>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.rcarz.jiraclient;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.kordamp.json.JSON;
import org.kordamp.json.JSONObject;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an issue attachment.
 */
public class Attachment extends Resource {

    private User author = null;
    private String filename = null;
    private Date created = null;
    private int size = 0;
    private String mimeType = null;
    private String content = null;

    /**
     * Creates an attachment from a JSON payload.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    protected Attachment(RestClient restclient, JSONObject json) {
        super(restclient);

        if (json != null)
            deserialise(json);
    }

    private void deserialise(JSONObject json) {
        Map map = json;

        self = Field.getString(map.get("self"));
        id = Field.getString(map.get("id"));
        if (StringUtils.isEmpty(id)) {
            id = getAttachmentId();
        }
        author = Field.getResource(User.class, map.get("author"), restclient);
        filename = Field.getString(map.get("filename"));
        created = Field.getDate(map.get("created"));
        size = Field.getInteger(map.get("size"));
        mimeType = Field.getString(map.get("mimeType"));
        content = Field.getString(map.get("content"));
    }

    /**
     * Retrieves the given attachment record.
     *
     * @param restclient REST client instance
     * @param id         Internal JIRA ID of the attachment
     * @return an attachment instance
     * @throws JiraException when the retrieval fails
     */
    public static Attachment get(RestClient restclient, String id) throws JiraException {
        JSON result;

        try {
            result = restclient.get(getBaseUri() + "attachment/" + id);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve attachment " + id, ex);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException("JSON payload is malformed");

        return new Attachment(restclient, (JSONObject) result);
    }

    /**
     * Downloads attachment to byte array
     *
     * @return a byte[]
     * @throws JiraException when the download fails
     */
    public byte[] download() throws JiraException {
        HttpResponse response = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            HttpGet get = new HttpGet(content);
            response = restclient.getHttpClient().execute(get);

            // check response
            StatusLine sl = response.getStatusLine();
            if (sl.getStatusCode() > 300) {
                throw new JiraException(String.format("Download failed for Attachment: %s with Status: %d - %s",
                        this.content, sl.getStatusCode(), sl.getReasonPhrase()));
            }

            // get content
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try (InputStream inputStream = entity.getContent()) {
                    int next = inputStream.read();
                    while (next > -1) {
                        bos.write(next);
                        next = inputStream.read();
                    }
                }
            } else {
                // should not happen, but anyway ...
                throw new JiraException("HttpResponse did not contain the Attachment-Content for: " + this.content);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new JiraException(String.format("Failed downloading attachment from %s: %s", this.content, e.getMessage()));
        } finally {
            // release http-connection in any case
            if (Objects.nonNull(response)) EntityUtils.consumeQuietly(response.getEntity());
        }
    }

    /**
     * Download the Attachment to a file (suitable for big-attachments).
     *
     * @param directory The directory where to put the file inside a sub-directory
     * @return The downloaded Attachment within the given directory
     * @throws IOException on any issue writing the attachment-file
     * @throws JiraException on any issue downloading the attachment-content
     */
    public File download(Path directory) throws IOException, JiraException {
        Path subDirectory = Files.createTempDirectory(directory, "attachment-" + getId());
        File download = new File(subDirectory.toFile(), getFileName());
        if (!download.createNewFile()) {
            throw new IOException("Failed to create local file for downloading the Attachment: " + download);
        }

        HttpResponse response = null;
        try (FileOutputStream out = new FileOutputStream(download);
             BufferedOutputStream writer = new BufferedOutputStream(out)) {
            URIBuilder ub = new URIBuilder(getContentUrl());
            HttpGet req = new HttpGet(ub.build());
            restclient.getCreds().authenticate(req);
            response = restclient.getHttpClient().execute(req);
            // check response
            StatusLine sl = response.getStatusLine();
            if (sl.getStatusCode() > 300) {
                throw new JiraException(String.format("Download failed for Attachment: %s with Status: %d - %s",
                        this.content, sl.getStatusCode(), sl.getReasonPhrase()));
            }

            response.getEntity().writeTo(writer);
        } catch (URISyntaxException | ClientProtocolException e) {
            // should not happen, but anyway
            throw new AssertionError("JIRA provided illegal Download-URL for attachment: " + getContentUrl());
        } finally {
            if (Objects.nonNull(response)) {
                EntityUtils.consumeQuietly(response.getEntity());
            }
        }
        return download;
    }

    @Override
    public String toString() {
        return getContentUrl();
    }

    public User getAuthor() {
        return author;
    }

    public Date getCreatedDate() {
        return created;
    }

    public String getContentUrl() {
        return content;
    }

    public String getFileName() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getSize() {
        return size;
    }

    private String getAttachmentId() {
        String[] parts = self.split("/");
        return parts[parts.length - 1];
    }
}

