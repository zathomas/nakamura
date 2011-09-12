require './features/support/ruby-lib-dir.rb'
require 'sling/sling'
require 'sling/users'

include SlingUsers
include SlingInterface

TESTUSERNAME = "student51"

@s = Sling.new()
@um = UserManager.new(@s)
@um.create_user("suzy", "Suzy", "Queue")
if @um.create_user(TESTUSERNAME, "Joe", "Student")
  puts "Created user '#{TESTUSERNAME}' for testing."
else
  puts "Using existing user '#{TESTUSERNAME}' for testing."
end