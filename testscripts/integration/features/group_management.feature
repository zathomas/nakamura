Feature: User/Group Management Operations

Scenario: I Can Add Myself as Member of a Joinable Collection
  Given There is a group "test-group"
    And the Group "test-group" is joinable
    And the Group "test-group" is a Collection
    And I have authenticated as User "alice"
  When I add member "alice" to Group "test-group"
  Then I become a member of Group "test-group"

@restore_user_registration
Scenario: I Can Turn Off User Self-Registration
  Given User self-registration is disabled
  When I try to create a new user
  Then My User was not created successfully

Scenario: Cannot Create User Without Name
  Given I am acting as the admin User
  When I try to create a new user with no name
  Then I receive an internal server error

Scenario: Cannot Create User Without Password
  Given I am acting as the admin User
  When I try to create a new user without password
  Then I receive an internal server error

Scenario: Cannot Create User Without Matching Password Confirmation
  Given I am acting as the admin User
  When I try to create a new user without matching password confirmation
  Then I receive an internal server error
