package in.ommishra.ledgerline.account;

import in.ommishra.ledgerline.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AccountApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void opensAndFetchesAnAccount() throws Exception {
        String location = mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wallet:api-1","name":"Asha's wallet","type":"LIABILITY","currency":"INR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.overdraftAllowed").value(false))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("wallet:api-1"));
    }

    @Test
    void rejectsDuplicateCode() throws Exception {
        String body = """
                {"code":"wallet:api-dup","name":"Wallet","type":"LIABILITY","currency":"INR"}
                """;
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("duplicate_account_code"));
    }

    @Test
    void validatesRequest() throws Exception {
        mvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"Bad Code","name":"","type":"LIABILITY","currency":"inr"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownAccountIs404() throws Exception {
        mvc.perform(get("/api/v1/accounts/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
