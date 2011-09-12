Feature: Smoke Test
  In order to know that the system is running
  As a developer
  I want to see certain basic tasks complete
  
  Scenario: Invalid login
    Given I am on the landing page
    When I enter "student51" in username
    And I enter "foobar" in password
    And I press Sign In
    Then I see "Invalid Username" on the page
    
  Scenario: Valid login
    Given I am on the landing page
    When I enter "student51" in username
    And I enter "testuser" in password
    And I press Sign In
    Then I see the dashboard page
    
  Scenario: Check Inbox
    When I click the inbox icon
    Then I see the inbox page
    
  Scenario: Compose Message
    Given I am on the inbox page
    When I click Compose message
    Then I see the new message window
    
  Scenario: Send Message
    Given I am in the new message window
    When I enter "suzy" in the To: box
    And I enter "Wassup?" in the Subject: box
    And I enter "Nice website. See ya." in the Body: box
    And I click Send Message
    Then I see an alert with "Your message has been sent"
    
  Scenario: Update Basic Profile Information
    Given I am on the Basic Information page
    When I enter "JJ" in the Preferred name: box
    And I click Update
    Then I see an alert with "Your profile information has been updated"