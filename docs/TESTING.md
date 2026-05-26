# Testing Guide

## Table of Contents

1. [Testing Philosophy](#1-testing-philosophy)
2. [Test Stack](#2-test-stack)
3. [Unit Testing](#3-unit-testing)
4. [Integration Testing](#4-integration-testing)
5. [End-to-End Testing](#5-end-to-end-testing)
6. [Frontend Testing](#6-frontend-testing)
7. [Coverage Requirements](#7-coverage-requirements)
8. [Running Tests](#8-running-tests)
9. [Test Data and Fixtures](#9-test-data-and-fixtures)
10. [CI/CD Integration](#10-cicd-integration)

---

## 1. Testing Philosophy

### The Testing Pyramid

```
        /\
       /E2E\          ← Small: browser-level, slow, high confidence
      /------\
     /  Integ  \      ← Medium: Spring context + real DB, catches wiring bugs
    /------------\
   /  Unit Tests  \   ← Large: fast, isolated, drives design
  /----------------\
```

### Guiding Principles

- **Test behaviour, not implementation.** Assert on what the code does (HTTP status, response body, database state), not how it does it (which private method was called, in what order).
- **Real databases for integration tests.** PostgreSQL semantics differ from H2. Use Testcontainers to spin up a real PostgreSQL instance so migrations and queries behave exactly as in production.
- **Decouple optional infrastructure.** Tests should run without Redis or Kafka. Use `@TestPropertySource` to set `redis.enabled=false` and `kafka.enabled=false` by default, enabling them only in dedicated infra tests.
- **Fail fast on the critical path.** Vote deduplication, JWT validation, poll ownership checks, and rate limiting must have 100% branch coverage.

---

## 2. Test Stack

### Backend

| Tool | Version | Purpose |
|------|---------|---------|
| JUnit 5 (`junit-jupiter`) | 5.10.x (via Spring Boot) | Test runner and assertions |
| Mockito | 5.x (via Spring Boot) | Mocking and stubbing |
| Spring Boot Test | 3.2.1 | `@SpringBootTest`, `MockMvc`, slice tests |
| Testcontainers (PostgreSQL) | 1.19.x | Real PostgreSQL in Docker for integration tests |
| AssertJ | 3.x (via Spring Boot) | Fluent assertions |
| Spring Security Test | 6.2.x | `@WithMockUser`, `SecurityMockMvcRequestPostProcessors` |

### Frontend

| Tool | Version | Purpose |
|------|---------|---------|
| Vitest | 1.x | Unit/component test runner (Vite-native, Jest-compatible API) |
| React Testing Library | 14.x | Component rendering and user interaction simulation |
| Mock Service Worker (MSW) | 2.x | API mocking at the network layer |
| `@testing-library/user-event` | 14.x | Realistic user event simulation |
| `jsdom` | 24.x | DOM simulation for Vitest |

### E2E

| Tool | Version | Purpose |
|------|---------|---------|
| Playwright | 1.44.x | Cross-browser E2E tests against the running application |

---

## 3. Unit Testing

Unit tests isolate a single class, mocking all collaborators. They must run in milliseconds and require no running infrastructure.

### Service Layer

Service classes hold all business logic and are the primary target for unit tests.

#### Example: VoteService — duplicate vote

```java
@ExtendWith(MockitoExtension.class)
class VoteServiceTest {

    @Mock PollRepository pollRepository;
    @Mock VoteRepository voteRepository;
    @Mock FreeTextVoteRepository freeTextVoteRepository;
    @Mock UserRepository userRepository;
    @Mock WebSocketEventPublisher webSocketEventPublisher;
    @Mock RedisVoteCacheService cacheService;   // nullable; injected as null below

    @InjectMocks VoteService voteService;

    @BeforeEach
    void setUp() {
        // Inject null for optional services to simulate Redis-off mode
        ReflectionTestUtils.setField(voteService, "cacheService", null);
        ReflectionTestUtils.setField(voteService, "trendingPollService", null);
    }

    @Test
    void castVote_duplicateVote_throws409() {
        UUID pollId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Poll poll = activeSingleChoicePoll(pollId);
        User user = user(userId, "bob");
        PollOption option = option(poll);

        when(pollRepository.findByIdWithDetails(pollId)).thenReturn(Optional.of(poll));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(voteRepository.existsByPoll_IdAndUser_Id(pollId, userId)).thenReturn(true);

        CastVoteRequest req = new CastVoteRequest();
        req.setOptionIds(List.of(option.getId()));

        assertThatThrownBy(() -> voteService.castVote(pollId, req, "bob"))
                .isInstanceOf(DuplicateVoteException.class);

        verify(voteRepository, never()).save(any());
    }

    @Test
    void castVote_closedPoll_throwsBadRequest() {
        UUID pollId = UUID.randomUUID();
        Poll poll = closedPoll(pollId);
        User user = user(UUID.randomUUID(), "bob");

        when(pollRepository.findByIdWithDetails(pollId)).thenReturn(Optional.of(poll));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));

        CastVoteRequest req = new CastVoteRequest();
        req.setOptionIds(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> voteService.castVote(pollId, req, "bob"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void castFreeTextVote_blankText_throwsBadRequest() {
        UUID pollId = UUID.randomUUID();
        Poll poll = activeFreeTextPoll(pollId);
        User user = user(UUID.randomUUID(), "bob");

        when(pollRepository.findByIdWithDetails(pollId)).thenReturn(Optional.of(poll));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        when(freeTextVoteRepository.existsByPoll_IdAndUser_Id(pollId, user.getId())).thenReturn(false);

        CastVoteRequest req = new CastVoteRequest();
        req.setFreeText("   ");

        assertThatThrownBy(() -> voteService.castVote(pollId, req, "bob"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("blank");
    }
}
```

#### Example: PollService — free-text vote count

```java
@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock PollRepository pollRepository;
    @Mock FreeTextVoteRepository freeTextVoteRepository;
    @Mock VoteRepository voteRepository;

    @InjectMocks PollService pollService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pollService, "cacheService", null);
        ReflectionTestUtils.setField(pollService, "trendingPollService", null);
    }

    @Test
    void getActivePolls_freeTextPoll_totalVotesFromFreeTextTable() {
        UUID pollId = UUID.randomUUID();
        Poll poll = freeTextPoll(pollId);

        when(pollRepository.findByStatus(eq(PollStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(poll)));
        when(freeTextVoteRepository.countByPoll_Id(pollId)).thenReturn(7L);

        Page<PollResponse> page = pollService.getActivePolls(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTotalVotes()).isEqualTo(7L);
    }

    @Test
    void getActivePolls_singleChoicePoll_totalVotesFromVoteRepository() {
        UUID pollId = UUID.randomUUID();
        Poll poll = singleChoicePoll(pollId);

        when(pollRepository.findByStatus(eq(PollStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(poll)));
        when(voteRepository.countVotesByOption(pollId)).thenReturn(
                List.of(new Object[]{poll.getOptions().get(0).getId().toString(), 5L},
                        new Object[]{poll.getOptions().get(1).getId().toString(), 3L}));

        Page<PollResponse> page = pollService.getActivePolls(PageRequest.of(0, 10));

        assertThat(page.getContent().get(0).getTotalVotes()).isEqualTo(8L);
    }
}
```

#### Example: JWT TokenProvider

```java
@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        // 256-bit base64-encoded secret used only in tests
        ReflectionTestUtils.setField(provider, "jwtSecret",
                "dGVzdC1zZWNyZXQta2V5LXRoYXQtaXMtbG9uZy1lbm91Z2gtZm9yLUhTMjU2");
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 86400000L);
    }

    @Test
    void generateAndValidateToken_roundTrip() {
        String token = provider.generateToken("alice");
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getUsernameFromToken(token)).isEqualTo("alice");
    }

    @Test
    void expiredToken_failsValidation() throws Exception {
        ReflectionTestUtils.setField(provider, "jwtExpirationMs", 1L); // 1 ms
        String token = provider.generateToken("alice");
        Thread.sleep(10);
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_failsValidation() {
        String token = provider.generateToken("alice");
        String tampered = token.substring(0, token.length() - 4) + "XXXX";
        assertThat(provider.validateToken(tampered)).isFalse();
    }
}
```

### Controller Layer (MockMvc Slice Tests)

Use `@WebMvcTest` to test the HTTP layer in isolation, mocking the service.

```java
@WebMvcTest(PollController.class)
@Import(SecurityConfig.class)
class PollControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PollService pollService;
    @MockBean JwtTokenProvider jwtTokenProvider;

    @Test
    void getPolls_noAuth_returns200() throws Exception {
        when(pollService.getActivePolls(any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/polls"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createPoll_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/polls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"pollType\":\"SINGLE_CHOICE\",\"options\":[\"A\",\"B\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice")
    void createPoll_validBody_returns201() throws Exception {
        PollResponse response = stubPollResponse();
        when(pollService.createPoll(any(), eq("alice"))).thenReturn(response);

        mockMvc.perform(post("/api/polls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Q?\",\"pollType\":\"SINGLE_CHOICE\",\"options\":[\"A\",\"B\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.question").value("Q?"));
    }
}
```

---

## 4. Integration Testing

Integration tests spin up the full Spring context with a real PostgreSQL database via Testcontainers. They verify end-to-end request handling including JPA queries, Flyway migrations, and security.

### Shared Base Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "redis.enabled=false",
        "kafka.enabled=false",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("polling_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired protected TestRestTemplate restTemplate;

    protected String registerAndLogin(String username) {
        // Register
        AuthRequest reg = new AuthRequest(username, username + "@test.com", "Password1!");
        restTemplate.postForEntity("/api/auth/register", reg, ApiResponse.class);

        // Login
        ResponseEntity<ApiResponse<AuthResponse>> login =
                restTemplate.postForEntity("/api/auth/login",
                        new LoginRequest(username, "Password1!"),
                        new ParameterizedTypeReference<>() {});
        return login.getBody().getData().getToken();
    }
}
```

### Vote Flow Integration Test

```java
class VoteIntegrationTest extends AbstractIntegrationTest {

    @Test
    void fullVoteFlow_singleChoice() {
        String token = registerAndLogin("alice_" + System.nanoTime());
        HttpHeaders headers = bearerHeaders(token);

        // 1. Create poll
        CreatePollRequest create = new CreatePollRequest(
                "Best framework?", PollType.SINGLE_CHOICE, List.of("Spring", "Quarkus"), null, null);
        ResponseEntity<ApiResponse<PollResponse>> created =
                restTemplate.exchange("/api/polls", HttpMethod.POST,
                        new HttpEntity<>(create, headers),
                        new ParameterizedTypeReference<>() {});

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID pollId = created.getBody().getData().getId();
        UUID optionId = created.getBody().getData().getOptions().get(0).getId();

        // 2. Cast vote
        CastVoteRequest vote = new CastVoteRequest();
        vote.setOptionIds(List.of(optionId));
        ResponseEntity<ApiResponse<PollResultsResponse>> voteResp =
                restTemplate.exchange("/api/polls/" + pollId + "/vote", HttpMethod.POST,
                        new HttpEntity<>(vote, headers),
                        new ParameterizedTypeReference<>() {});

        assertThat(voteResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(voteResp.getBody().getData().getTotalResponses()).isEqualTo(1);

        // 3. Duplicate vote returns 409
        ResponseEntity<Object> dup =
                restTemplate.exchange("/api/polls/" + pollId + "/vote", HttpMethod.POST,
                        new HttpEntity<>(vote, headers), Object.class);

        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 4. Results endpoint reflects count
        ResponseEntity<ApiResponse<PollResultsResponse>> results =
                restTemplate.getForEntity("/api/polls/" + pollId + "/results",
                        new ParameterizedTypeReference<>() {});

        assertThat(results.getBody().getData().getOptionResults())
                .anyMatch(r -> r.getVoteCount() == 1);
    }

    @Test
    void freeTextVote_savesResponseAndIncrementsCount() {
        String token = registerAndLogin("bob_" + System.nanoTime());
        HttpHeaders headers = bearerHeaders(token);

        // Create free-text poll
        CreatePollRequest create = new CreatePollRequest(
                "What should we build?", PollType.FREE_TEXT, null, null, null);
        ResponseEntity<ApiResponse<PollResponse>> created =
                restTemplate.exchange("/api/polls", HttpMethod.POST,
                        new HttpEntity<>(create, headers),
                        new ParameterizedTypeReference<>() {});
        UUID pollId = created.getBody().getData().getId();

        // Cast free-text vote
        CastVoteRequest vote = new CastVoteRequest();
        vote.setFreeText("Dark mode support");
        ResponseEntity<ApiResponse<PollResultsResponse>> voteResp =
                restTemplate.exchange("/api/polls/" + pollId + "/vote", HttpMethod.POST,
                        new HttpEntity<>(vote, headers),
                        new ParameterizedTypeReference<>() {});

        assertThat(voteResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(voteResp.getBody().getData().getTotalResponses()).isEqualTo(1);
        assertThat(voteResp.getBody().getData().getFreeTextResponses())
                .contains("Dark mode support");

        // Feed reflects correct totalVotes
        ResponseEntity<ApiResponse<Page<PollResponse>>> feed =
                restTemplate.getForEntity("/api/polls",
                        new ParameterizedTypeReference<>() {});

        PollResponse feedPoll = feed.getBody().getData().getContent().stream()
                .filter(p -> p.getId().equals(pollId))
                .findFirst().orElseThrow();
        assertThat(feedPoll.getTotalVotes()).isEqualTo(1L);
    }

    @Test
    void closePoll_nonOwner_returns403() {
        String aliceToken = registerAndLogin("alice2_" + System.nanoTime());
        String bobToken   = registerAndLogin("bob2_"   + System.nanoTime());

        // Alice creates poll
        CreatePollRequest create = new CreatePollRequest(
                "Alice's poll", PollType.SINGLE_CHOICE, List.of("Y", "N"), null, null);
        ResponseEntity<ApiResponse<PollResponse>> created =
                restTemplate.exchange("/api/polls", HttpMethod.POST,
                        new HttpEntity<>(create, bearerHeaders(aliceToken)),
                        new ParameterizedTypeReference<>() {});
        UUID pollId = created.getBody().getData().getId();

        // Bob tries to close it
        ResponseEntity<Object> close =
                restTemplate.exchange("/api/polls/" + pollId + "/close", HttpMethod.PATCH,
                        new HttpEntity<>(null, bearerHeaders(bobToken)), Object.class);

        assertThat(close.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
```

### Auth Integration Tests

```java
class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void register_duplicateUsername_returns400() {
        AuthRequest req = new AuthRequest("dupeuser", "dupe@test.com", "Password1!");
        restTemplate.postForEntity("/api/auth/register", req, Object.class);

        ResponseEntity<Object> second =
                restTemplate.postForEntity("/api/auth/register", req, Object.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_wrongPassword_returns401() {
        AuthRequest reg = new AuthRequest("wrongpass", "wrongpass@test.com", "Correct1!");
        restTemplate.postForEntity("/api/auth/register", reg, Object.class);

        LoginRequest bad = new LoginRequest("wrongpass", "WrongPassword!");
        ResponseEntity<Object> resp =
                restTemplate.postForEntity("/api/auth/login", bad, Object.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_expiredToken_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("eyJhbGciOiJIUzI1NiJ9.expired.signature");

        ResponseEntity<Object> resp =
                restTemplate.exchange("/api/polls", HttpMethod.POST,
                        new HttpEntity<>("{}", headers), Object.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

### Repository Tests

Use `@DataJpaTest` with the Testcontainers PostgreSQL container for lightweight repository-layer tests.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class PollRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired PollRepository pollRepository;
    @Autowired UserRepository userRepository;

    @Test
    void findByIdWithDetails_freeTextPoll_returnsEmptyOptions() {
        User user = userRepository.save(testUser("repo_test"));
        Poll poll = new Poll();
        poll.setQuestion("What do you think?");
        poll.setPollType(PollType.FREE_TEXT);
        poll.setStatus(PollStatus.ACTIVE);
        poll.setCreatedBy(user);
        Poll saved = pollRepository.save(poll);

        Optional<Poll> found = pollRepository.findByIdWithDetails(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOptions()).isEmpty(); // LEFT JOIN FETCH — no NPE
    }
}
```

---

## 5. End-to-End Testing

E2E tests run against the full application stack (backend + frontend + PostgreSQL). They verify user journeys through a real browser.

### Setup

```bash
# 1. Start the full stack
docker compose up -d

# 2. Install Playwright
cd e2e
npm install
npx playwright install chromium firefox

# 3. Run E2E tests
npx playwright test
```

### Project Structure

```
e2e/
├── playwright.config.ts
├── fixtures/
│   └── auth.fixture.ts        # shared login helper
├── pages/
│   ├── LoginPage.ts           # Page Object Model
│   ├── RegisterPage.ts
│   ├── HomePage.ts
│   └── PollDetailPage.ts
└── tests/
    ├── auth.spec.ts
    ├── polls.spec.ts
    └── realtime.spec.ts
```

### Playwright Configuration

```typescript
// e2e/playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  use: {
    baseURL: 'http://localhost:80',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { browserName: 'chromium' } },
    { name: 'firefox',  use: { browserName: 'firefox' } },
  ],
});
```

### Auth Flow

```typescript
// e2e/tests/auth.spec.ts
import { test, expect } from '@playwright/test';

test('register, then log in', async ({ page }) => {
  const username = `user_${Date.now()}`;

  // Register
  await page.goto('/register');
  await page.fill('[name=username]', username);
  await page.fill('[name=email]', `${username}@test.com`);
  await page.fill('[name=password]', 'Password1!');
  await page.click('button[type=submit]');
  await expect(page).toHaveURL('/');

  // Log out
  await page.click('[data-testid=logout-button]');

  // Log in again
  await page.goto('/login');
  await page.fill('[name=username]', username);
  await page.fill('[name=password]', 'Password1!');
  await page.click('button[type=submit]');
  await expect(page).toHaveURL('/');
  await expect(page.locator('[data-testid=username-display]')).toContainText(username);
});
```

### Poll Creation and Voting

```typescript
// e2e/tests/polls.spec.ts
import { test, expect } from '@playwright/test';
import { loginAs } from '../fixtures/auth.fixture';

test('create single-choice poll and vote on it', async ({ page }) => {
  await loginAs(page, 'alice');

  // Create poll
  await page.click('[data-testid=create-poll-button]');
  await page.fill('[name=question]', 'E2E: Best testing tool?');
  await page.fill('[name=options.0]', 'Playwright');
  await page.fill('[name=options.1]', 'Cypress');
  await page.click('button[data-testid=submit-poll]');

  // Verify on feed
  await expect(page.locator('text=E2E: Best testing tool?')).toBeVisible();

  // Open poll and vote
  await page.click('text=E2E: Best testing tool?');
  await page.click('label:has-text("Playwright")');
  await page.click('[data-testid=vote-button]');

  // Success banner appears
  await expect(page.locator('text=Vote recorded!')).toBeVisible();

  // Voting form is hidden
  await expect(page.locator('[data-testid=vote-button]')).not.toBeVisible();

  // Results bar shows 100%
  await expect(page.locator('[data-testid=result-bar-Playwright]')).toContainText('100%');
});

test('duplicate vote shows amber error banner', async ({ page }) => {
  await loginAs(page, 'bob');

  // Navigate to an existing active poll that bob hasn't voted on
  await page.goto('/');
  await page.click('[data-testid=poll-card]:first-child');

  // Vote
  await page.click('input[type=radio]:first-child');
  await page.click('[data-testid=vote-button]');
  await expect(page.locator('text=Vote recorded!')).toBeVisible();

  // Try to vote again by reloading and re-submitting (simulates second attempt)
  await page.reload();
  await expect(page.locator('text=You have already voted')).toBeVisible();
  await expect(page.locator('[data-testid=vote-button]')).not.toBeVisible();
});

test('free-text poll accepts text response', async ({ page }) => {
  await loginAs(page, 'carol');

  // Create free-text poll
  await page.click('[data-testid=create-poll-button]');
  await page.fill('[name=question]', 'What should we build?');
  await page.selectOption('[name=pollType]', 'FREE_TEXT');
  await page.click('button[data-testid=submit-poll]');

  // Vote with text
  await page.click('text=What should we build?');
  await page.fill('textarea[name=freeText]', 'Better mobile support');
  await page.click('[data-testid=vote-button]');

  await expect(page.locator('text=Vote recorded!')).toBeVisible();
  await expect(page.locator('text=Better mobile support')).toBeVisible();
});
```

### Real-Time Updates

```typescript
// e2e/tests/realtime.spec.ts
import { test, expect, chromium } from '@playwright/test';
import { loginAs } from '../fixtures/auth.fixture';

test('vote in tab A updates results in tab B within 1 second', async () => {
  const browser = await chromium.launch();
  const contextA = await browser.newContext();
  const contextB = await browser.newContext();
  const pageA = await contextA.newPage();
  const pageB = await contextB.newPage();

  // Create a poll as alice in tab A
  await loginAs(pageA, 'rt_alice');
  await pageA.click('[data-testid=create-poll-button]');
  await pageA.fill('[name=question]', 'RT test poll');
  await pageA.fill('[name=options.0]', 'Yes');
  await pageA.fill('[name=options.1]', 'No');
  await pageA.click('button[data-testid=submit-poll]');
  await pageA.click('text=RT test poll');

  // Bob opens the same poll in tab B (logged out — read-only viewer)
  const pollUrl = pageA.url();
  await pageB.goto(pollUrl);

  // Alice votes
  await pageA.click('label:has-text("Yes")');
  await pageA.click('[data-testid=vote-button]');

  // Tab B should update within 1 second (real-time)
  await expect(pageB.locator('[data-testid=result-bar-Yes]'))
        .toContainText('100%', { timeout: 1500 });

  await browser.close();
});
```

---

## 6. Frontend Testing

Frontend unit and component tests use Vitest and React Testing Library.

### Setup

```bash
cd frontend
npm run test          # run all tests
npm run test:watch    # watch mode
npm run test:coverage # coverage report
```

### Component Test: VoteOptions

```typescript
// frontend/src/components/poll/__tests__/VoteOptions.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { VoteOptions } from '../VoteOptions';

const singleChoiceOptions = [
  { id: 'opt-1', optionText: 'Spring Boot', voteCount: 0 },
  { id: 'opt-2', optionText: 'Django',      voteCount: 0 },
];

test('single-choice renders radio buttons', () => {
  render(
    <VoteOptions
      pollType="SINGLE_CHOICE"
      options={singleChoiceOptions}
      maxChoices={1}
      onVote={vi.fn()}
    />
  );
  expect(screen.getAllByRole('radio')).toHaveLength(2);
});

test('calls onVote with selected option ID', async () => {
  const onVote = vi.fn();
  render(
    <VoteOptions
      pollType="SINGLE_CHOICE"
      options={singleChoiceOptions}
      maxChoices={1}
      onVote={onVote}
    />
  );

  fireEvent.click(screen.getByLabelText('Spring Boot'));
  fireEvent.click(screen.getByRole('button', { name: /vote/i }));

  expect(onVote).toHaveBeenCalledWith({ optionIds: ['opt-1'] });
});

test('free-text renders textarea', () => {
  render(
    <VoteOptions
      pollType="FREE_TEXT"
      options={[]}
      maxChoices={0}
      onVote={vi.fn()}
    />
  );
  expect(screen.getByRole('textbox')).toBeInTheDocument();
});
```

### Component Test: PollDetailPage duplicate vote state

```typescript
// frontend/src/pages/__tests__/PollDetailPage.test.tsx
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { server } from '../../mocks/server'; // MSW server
import { http, HttpResponse } from 'msw';
import { PollDetailPage } from '../PollDetailPage';

test('409 response shows amber banner, hides vote form, keeps results visible', async () => {
  server.use(
    http.post('/api/polls/:pollId/vote', () =>
      HttpResponse.json({ status: 409, message: 'Already voted' }, { status: 409 })
    )
  );

  render(<PollDetailPage />, { wrapper: AuthenticatedRouter });

  const radio = await screen.findByLabelText('Option A');
  await userEvent.click(radio);
  await userEvent.click(screen.getByRole('button', { name: /vote/i }));

  await waitFor(() => {
    expect(screen.getByText(/already voted/i)).toBeInTheDocument();
    expect(screen.queryByText(/vote recorded/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /vote/i })).not.toBeInTheDocument();
    expect(screen.getByTestId('vote-results')).toBeInTheDocument();
  });
});
```

### MSW Setup

```typescript
// frontend/src/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/polls', () =>
    HttpResponse.json({
      success: true,
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 }
    })
  ),
  http.get('/api/polls/:pollId', ({ params }) =>
    HttpResponse.json({ success: true, data: mockPoll(params.pollId as string) })
  ),
];
```

```typescript
// frontend/src/mocks/server.ts  (Node / Vitest environment)
import { setupServer } from 'msw/node';
import { handlers } from './handlers';
export const server = setupServer(...handlers);
```

```typescript
// frontend/vitest.setup.ts
import { beforeAll, afterAll, afterEach } from 'vitest';
import { server } from './src/mocks/server';
beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
```

### Zustand Store Tests

```typescript
// frontend/src/store/__tests__/authStore.test.ts
import { act } from 'react';
import { useAuthStore } from '../authStore';

test('login sets token and username', () => {
  act(() => {
    useAuthStore.getState().login({ token: 'tok', username: 'alice', role: 'USER', expiresIn: 86400000 });
  });

  const state = useAuthStore.getState();
  expect(state.isAuthenticated).toBe(true);
  expect(state.username).toBe('alice');
});

test('logout clears authentication state', () => {
  act(() => useAuthStore.getState().login({ token: 'tok', username: 'alice', role: 'USER', expiresIn: 86400000 }));
  act(() => useAuthStore.getState().logout());

  expect(useAuthStore.getState().isAuthenticated).toBe(false);
  expect(useAuthStore.getState().token).toBeNull();
});
```

---

## 7. Coverage Requirements

### Backend

| Layer | Minimum Line Coverage | Notes |
|-------|-----------------------|-------|
| Service (VoteService, PollService) | 85% | All poll types, all error paths |
| Service (AuthService) | 80% | Happy path + duplicate user |
| JWT (JwtTokenProvider) | 100% | Security-critical |
| Vote deduplication path | 100% | Branch coverage — every duplicate check |
| Poll ownership check | 100% | Every 403 branch |
| Controllers | 70% | Slice tests cover HTTP layer |
| Repositories | Verified by integration tests | No mocking |
| Optional services (Redis/Kafka) | Verified by unit + null-guard tests | Both null and non-null paths |

Run coverage:
```bash
cd backend
./mvnw test jacoco:report
# Report: target/site/jacoco/index.html
```

JaCoCo minimum thresholds (enforced in CI):
```xml
<!-- pom.xml — jacoco-maven-plugin configuration -->
<configuration>
  <rules>
    <rule>
      <element>BUNDLE</element>
      <limits>
        <limit>
          <counter>LINE</counter>
          <value>COVEREDRATIO</value>
          <minimum>0.75</minimum>
        </limit>
        <limit>
          <counter>BRANCH</counter>
          <value>COVEREDRATIO</value>
          <minimum>0.70</minimum>
        </limit>
      </limits>
    </rule>
  </rules>
</configuration>
```

### Frontend

| Area | Minimum Coverage |
|------|-----------------|
| Zustand stores | 90% |
| Service layer (`pollService`, `authService`) | 80% |
| Critical UI paths (vote flow, 409 handling) | 100% |
| Utility functions | 90% |

Run coverage:
```bash
cd frontend
npm run test:coverage
# Report: coverage/index.html
```

---

## 8. Running Tests

### Backend

```bash
cd backend

# All tests (unit + integration — requires Docker for Testcontainers)
./mvnw test

# Unit tests only (fast, no Docker required)
./mvnw test -Dgroups=unit

# Integration tests only
./mvnw test -Dgroups=integration

# Single test class
./mvnw test -Dtest=VoteServiceTest

# With JaCoCo coverage report
./mvnw test jacoco:report
```

Mark test classes with JUnit 5 tags to separate them:
```java
@Tag("unit")
class VoteServiceTest { ... }

@Tag("integration")
class VoteIntegrationTest extends AbstractIntegrationTest { ... }
```

### Frontend

```bash
cd frontend

npm run test               # run all tests once
npm run test:watch         # watch mode (re-runs on file change)
npm run test:coverage      # with coverage report
npm run test -- VoteOptions # run a single test file
```

### E2E

```bash
cd e2e

# All browsers
npx playwright test

# Chromium only
npx playwright test --project=chromium

# Single spec
npx playwright test tests/polls.spec.ts

# Debug mode (headed)
npx playwright test --headed --debug

# View last HTML report
npx playwright show-report
```

---

## 9. Test Data and Fixtures

### Backend Test Factories

Centralise entity construction in a `TestFixtures` utility class:

```java
// src/test/java/com/polling/platform/util/TestFixtures.java
public final class TestFixtures {

    public static User user(UUID id, String username) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setUsername(username);
        u.setEmail(username + "@test.com");
        u.setPassword("hashed");
        u.setRole(Role.USER);
        return u;
    }

    public static Poll activeSingleChoicePoll(UUID id) {
        Poll p = new Poll();
        ReflectionTestUtils.setField(p, "id", id);
        p.setQuestion("Test question");
        p.setPollType(PollType.SINGLE_CHOICE);
        p.setStatus(PollStatus.ACTIVE);
        p.setMaxChoices(1);
        PollOption opt = new PollOption();
        ReflectionTestUtils.setField(opt, "id", UUID.randomUUID());
        opt.setOptionText("Option A");
        p.setOptions(new ArrayList<>(List.of(opt)));
        return p;
    }

    public static Poll activeFreeTextPoll(UUID id) {
        Poll p = new Poll();
        ReflectionTestUtils.setField(p, "id", id);
        p.setQuestion("Open question");
        p.setPollType(PollType.FREE_TEXT);
        p.setStatus(PollStatus.ACTIVE);
        p.setOptions(new ArrayList<>());
        return p;
    }

    public static Poll closedPoll(UUID id) {
        Poll p = activeSingleChoicePoll(id);
        p.setStatus(PollStatus.CLOSED);
        return p;
    }
}
```

### Frontend Mock Data

```typescript
// frontend/src/mocks/fixtures.ts
export const mockSingleChoicePoll = (overrides = {}): Poll => ({
  id: 'poll-1',
  question: 'Best framework?',
  createdBy: 'alice',
  status: 'ACTIVE',
  pollType: 'SINGLE_CHOICE',
  maxChoices: 1,
  options: [
    { id: 'opt-1', optionText: 'Spring Boot', voteCount: 5 },
    { id: 'opt-2', optionText: 'Django',      voteCount: 3 },
  ],
  totalVotes: 8,
  createdAt: '2026-01-01T00:00:00',
  expiresAt: null,
  ...overrides,
});

export const mockFreeTextPoll = (overrides = {}): Poll => ({
  id: 'poll-2',
  question: 'What should we build?',
  createdBy: 'alice',
  status: 'ACTIVE',
  pollType: 'FREE_TEXT',
  maxChoices: 0,
  options: [],
  totalVotes: 3,
  createdAt: '2026-01-01T00:00:00',
  expiresAt: null,
  ...overrides,
});
```

---

## 10. CI/CD Integration

### GitHub Actions

```yaml
# .github/workflows/test.yml
name: Test

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  backend:
    name: Backend Tests
    runs-on: ubuntu-latest
    services:
      # Testcontainers manages its own containers — no Docker Compose service needed
      docker:
        image: docker:dind
        options: --privileged
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - name: Cache Maven
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository
          key: maven-${{ hashFiles('backend/pom.xml') }}
      - name: Run tests
        working-directory: backend
        run: ./mvnw verify
      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: backend-coverage
          path: backend/target/site/jacoco

  frontend:
    name: Frontend Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - name: Install
        working-directory: frontend
        run: npm ci
      - name: Type check
        working-directory: frontend
        run: npm run type-check
      - name: Lint
        working-directory: frontend
        run: npm run lint
      - name: Test with coverage
        working-directory: frontend
        run: npm run test:coverage
      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: frontend-coverage
          path: frontend/coverage

  e2e:
    name: E2E Tests
    runs-on: ubuntu-latest
    needs: [backend, frontend]
    steps:
      - uses: actions/checkout@v4
      - name: Start stack
        run: docker compose up -d --build
      - name: Wait for health
        run: |
          for i in {1..30}; do
            curl -sf http://localhost:8080/health && break
            sleep 5
          done
      - uses: actions/setup-node@v4
        with: { node-version: '20' }
      - name: Install Playwright
        working-directory: e2e
        run: npm ci && npx playwright install --with-deps chromium
      - name: Run E2E
        working-directory: e2e
        run: npx playwright test --project=chromium
      - name: Upload traces on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-report
          path: e2e/playwright-report
```

### Pull Request Gates

All of the following must pass before merging to `main`:

| Check | Tool | Failure blocks merge |
|-------|------|----------------------|
| Backend unit tests | JUnit 5 | Yes |
| Backend integration tests | JUnit 5 + Testcontainers | Yes |
| JaCoCo line coverage ≥ 75% | JaCoCo | Yes |
| Frontend type check | `tsc --noEmit` | Yes |
| Frontend lint | ESLint | Yes |
| Frontend unit tests | Vitest | Yes |
| E2E happy paths | Playwright | Yes |
| Docker image builds | `docker compose build` | Yes |
