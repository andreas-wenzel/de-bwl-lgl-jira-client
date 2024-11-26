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

import org.kordamp.json.JSON;
import org.kordamp.json.JSONArray;
import org.kordamp.json.JSONObject;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents a JIRA user.
 */
public class User extends Resource {

    private boolean active = false;
    private Map<String, String> avatarUrls = null;
    private String displayName = null;
    private String email = null;
    private String name = null;

    private Collection<Group> groups = null;

    /**
     * Creates a user from a JSON payload.
     *
     * @param restclient REST client instance
     * @param json       JSON payload
     */
    protected User(RestClient restclient, JSONObject json) {
        super(restclient);

        if (json != null)
            deserialise(json);
    }

    public static User create(RestClient restclient, String username, String email, String displayName) throws JiraException {
        JSON payload = new JSONObject()
                .accumulate("name", username)
                .accumulate("key", username)
                .accumulate("emailAddress", email)
                .accumulate("displayName", displayName);

        JSON result;
        try {
            result = restclient.post(getBaseUri() + "user", payload);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve user " + username, ex);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException("JSON payload is malformed");

        return new User(restclient, (JSONObject) result);
    }

    /**
     * Retrieves the given user record including the memberships in groups.
     *
     * @param restclient REST client instance
     * @param username   User logon name
     * @return a user instance
     * @throws JiraException when the retrieval fails
     */
    public static User get(RestClient restclient, String username)
            throws JiraException {

        JSON result;

        Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("expand", "groups");

        try {
            result = restclient.get(getBaseUri() + "user", params);
        } catch (Exception ex) {
            throw new JiraException("Failed to retrieve user " + username, ex);
        }

        if (!(result instanceof JSONObject))
            throw new JiraException("JSON payload is malformed");

        return new User(restclient, (JSONObject) result);
    }

    /**
     * Searches for User by their name.
     *
     * @param restClient REST client instance
     * @param name       User logon name to search for
     * @return All matching users or empty list if nothing found.
     * @throws JiraException when the retrieval fails
     */
    public static Collection<User> searchUser(RestClient restClient, String name) throws JiraException {
        JSON result;

        Map<String, String> params = new HashMap<>();
        params.put("username", name);
        params.put("includeInactive", Boolean.TRUE.toString());

        try {
            result = restClient.get(getBaseUri() + "user/search", params);
        } catch (Exception ex) {
            throw new JiraException("Failed to search users with query: " + name, ex);
        }

        if (!(result instanceof JSONArray))
            throw new JiraException("JSON payload is malformed");

        Collection<User> users = ((JSONArray) result).stream()
                .map(obj -> new User(restClient, (JSONObject) obj))
                .collect(Collectors.toList());
        return users;
    }

    private void deserialise(JSONObject json) {
        Map map = json;

        self = Field.getString(map.get("self"));
        id = Field.getString(map.get("id"));
        active = Field.getBoolean(map.get("active"));
        avatarUrls = Field.getMap(String.class, String.class, map.get("avatarUrls"));
        displayName = Field.getString(map.get("displayName"));
        email = getEmailFromMap(map);
        name = Field.getString(map.get("name"));

        if (json.containsKey("groups")) {
            groups = Field.getResourceArray(Group.class, json.getJSONObject("groups").getJSONArray("items"), restclient);
        }
    }

    /**
     * API changes email address might be represented as either "email" or "emailAddress"
     *
     * @param map JSON object for the User
     * @return String email address of the JIRA user.
     */
    private String getEmailFromMap(Map map) {
        if (map.containsKey("email")) {
            return Field.getString(map.get("email"));
        } else {
            return Field.getString(map.get("emailAddress"));
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean isActive() {
        return active;
    }

    public Map<String, String> getAvatarUrls() {
        return avatarUrls;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public void setActive() throws JiraException {
        if (!isActive()) {
            toggleState(Boolean.TRUE);
            active = true;
        }
    }

    public void setInactive() throws JiraException {
        if (isActive()) {
            toggleState(Boolean.FALSE);
            active = false;
        }
    }

    /**
     * Get the groups this user is member of
     *
     * @return The groups
     * @throws JiraException failed to obtain the groups
     */
    public Collection<Group> getGroups() throws JiraException {
        if (groups == null) {
            User.get(restclient, name);
        }
        return groups;
    }

    /**
     * Deletes this user. If deletion fails please review JIRA-Rest-API-Docs
     *
     * @throws JiraException on any problem deleting the user
     */
    public void delete() throws JiraException {
        try {
            restclient.delete(restclient.buildURI(getBaseUri() + "user",
                    Map.of("username", name)));
        } catch (Exception ex) {
            throw new JiraException("Failed to delete user " + name, ex);
        }
    }

    /**
     * Changes the Password of the user
     *
     * @param secret The new password for the user
     * @throws JiraException on any problem
     */
    public void changePassword(String secret) throws JiraException {
        try {
            restclient.put(restclient.buildURI(getBaseUri() + "user/password", Map.of("username", name)),
                    new JSONObject().accumulate("password", secret));
        } catch (Exception e) {
            throw new JiraException("Failed to change password for User: " + name, e);
        }
    }

    /**
     * Schedules a user-anonymization-process. Requires admin permission
     * @throws JiraException on any problem scheduling this task
     */
    public void anonymize() throws JiraException {
        try {
            restclient.post(restclient.buildURI(getBaseUri() + "user/anonymization"),
                    new JSONObject()
                            .accumulate("userKey", name)
                            .accumulate("newOwnerKey", "admin"));
        } catch (Exception e) {
            throw new JiraException("Failed to schedule User-Anonymization for: "+ name, e);
        }
    }

    private void toggleState(Boolean active) throws JiraException {
        JSONObject json = new JSONObject()
                .accumulate("username", getName())
                .accumulate("key", getName())
                .accumulate("active", active.toString());
        try {
            URI updateUser = restclient.buildURI(getBaseUri() + "user", Collections.singletonMap("username", getName()));
            restclient.put(updateUser, json);
        } catch (Exception ex) {
            throw new JiraException("Failed to activate user: " + getName(), ex);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(name, user.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

