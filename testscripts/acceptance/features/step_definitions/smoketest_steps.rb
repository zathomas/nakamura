require 'watir-webdriver'

browser = Watir::Browser.new

Given /^I am on the landing page$/ do
  browser.goto 'http://localhost:8080/'
  browser.button(:id => "topnavigation_user_options_login").click
end

When /^I enter "(.+)" in username$/ do |username|
  browser.text_field(:id => "topnavigation_user_options_login_fields_username").set username
end

When /^I enter "(.+)" in password$/ do |password|
  browser.text_field(:id => "topnavigation_user_options_login_fields_password").set password
end

When /^I press Sign In$/ do
  browser.button(:id => "topnavigation_user_options_login_button_login").click
end

Then /^I see "(.+)" on the page$/ do |text|
  browser.text.should include(text)
end

Then /^I see the dashboard page$/ do
  wait_for(browser, "entity_container", 5)
  browser.text.should include("My Dashboard")
end

When /^I click the inbox icon$/ do
  browser.div(:id => "topnavigation_user_inbox_icon").click
end

Then /^I see the inbox page$/ do
  browser.div(:id => "inbox_header").exists?
end

Given /^I am on the inbox page$/ do
  browser.goto 'http://localhost:8080/me#l=messages/inbox'
end

When /^I click Compose message$/ do
  browser.element(:id => "inbox_create_new_message").click
end

Then /^I see the new message window$/ do
  browser.div(:id => "message_form").visible?
end

Given /^I am in the new message window$/ do
  browser.goto 'http://localhost:8080/me#l=messages/inbox&newmessage=true'
end

When /^I enter "(.+)" in the To: box$/ do |to|
  browser.text_field(:id => "sendmessage_to_autoSuggest").set to
  sleep 1 #ajax takes a second
  browser.element(:id => "as-result-item-0").click
end

When /^I enter "(.+)" in the Subject: box$/ do |subject|
  browser.text_field(:id => "comp-subject").set subject
end

When /^I enter "(.+)" in the Body: box$/ do |body|
  browser.text_field(:id => "comp-body").set body
end

When /^I click Send Message$/ do
  browser.button(:id => "send_message").click
end

Then /^I see an alert with "(.+)"$/ do |alert|
  wait_for(browser, "gritter_notice_wrapper", 2)
  browser.text.should include(alert)
end

Given /^I am on the Basic Information page$/ do
  browser.goto 'http://localhost:8080/me#l=profile/basic'
end

When /^I enter "(.+)" in the Preferred name: box$/ do |name|
  wait_for(browser, "profilesection_generalinfo_basic_elements_preferredName", 2)
  browser.text_field(:id => "profilesection_generalinfo_basic_elements_preferredName").set name
end

When /^I click Update$/ do
  browser.form(:id, /^profile_form_profilesection-basic-/).submit
end


def wait_for(browser, element, timeout = 10)
  timeout.times do
    break if browser.element(:id => element).exists?
    sleep 1
  end
end