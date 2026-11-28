package com.example.pettracker;

import com.example.pettracker.entity.Pet;
import com.example.pettracker.entity.User;
import com.example.pettracker.repository.PetRepository;
import com.example.pettracker.repository.UserRepository;
import com.example.pettracker.security.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PettrackerApplicationTests {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private PetRepository pets;
    @Autowired private JwtProvider jwtProvider;

    @Test
    void contextLoads() {
    }

    @Test
    @Transactional
    void removedVolunteerCanRequestPrivateReentryAndOnlyCreatorCanApprove() throws Exception {
        User owner = createGroupTestUser("Owner");
        User volunteer = createGroupTestUser("Volunteer");
        Pet pet = pets.save(Pet.builder().owner(owner).name("Milo").status(Pet.Status.LOST).build());
        String ownerToken = bearer(owner);
        String volunteerToken = bearer(volunteer);
        String createBody = json.createObjectNode().put("petId", pet.getId())
                .put("groupName", "Find Milo").put("visibility", "PUBLIC").toString();
        String created = mvc.perform(post("/api/search-groups")
                        .header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = json.readTree(created).required("id").asLong();
        String groupPath = "/api/search-groups/" + groupId;
        String joinPath = groupPath + "/join";

        mvc.perform(post(joinPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.volunteer.active").value(true));
        mvc.perform(delete(groupPath + "/volunteers/{userId}", volunteer.getId())
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(put(groupPath).header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(groupPath).header(HttpHeaders.AUTHORIZATION, volunteerToken))
                .andExpect(status().isNotFound());

        String optionsPath = "/api/search-groups/my/rejoin-options";
        mvc.perform(get(optionsPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].groupId").value(groupId))
                .andExpect(jsonPath("$[0].pendingRequestId").doesNotExist());
        String pending = mvc.perform(post(joinPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestAlreadyPending").value(false))
                .andReturn().getResponse().getContentAsString();
        long requestId = json.readTree(pending).required("rejoinRequestId").asLong();
        mvc.perform(post(joinPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestAlreadyPending").value(true))
                .andExpect(jsonPath("$.rejoinRequestId").value(requestId));
        mvc.perform(get(optionsPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pendingRequestId").value(requestId));

        String approvePath = groupPath + "/rejoin-requests/" + requestId + "/approve";
        mvc.perform(post(approvePath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
        mvc.perform(post(approvePath).header(HttpHeaders.AUTHORIZATION, ownerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(get(groupPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeMember").value(true));
        mvc.perform(get(optionsPath).header(HttpHeaders.AUTHORIZATION, volunteerToken)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private User createGroupTestUser(String name) {
        return users.save(User.builder().name(name).email(UUID.randomUUID() + "@example.test")
                .password("unused-test-password").role(User.Role.USER).build());
    }

    private String bearer(User user) {
        return "Bearer " + jwtProvider.generateToken(user.getEmail());
    }
}
