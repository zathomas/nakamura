#!/usr/bin/env ruby


require 'nakamura/test'
require 'test/unit.rb'
include SlingUsers


class TC_Kern2263 < Test::Unit::TestCase
  include SlingTest

  def test_group_is_really_deleted
    m = Time.now.to_nsec
    
    # create a group
    group = Group.new("g-test-#{m}")
    @s.switch_user(User.admin_user())
    res = @s.execute_post(@s.url_for("#{$GROUP_URI}"), {
      ":name" => group.name,
      "_charset_" => "UTF-8"
    })
    
    # confirm that we can read the group and the group home
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{group.name}.tidy.json"))
    assert_equal("200", res.code, "New group content is missing")
    res = @s.execute_get(@s.url_for("/~#{group.name}.tidy.json"))
    assert_equal("200", res.code, "New group home is missing")
    
    # delete the group
    @s.execute_post(@s.url_for("/system/userManager/group/#{group.name}.delete.html"), {
      ":status" => "standard"
    })
    
    # confirm that the group and the group home are gone
    res = @s.execute_get(@s.url_for("/system/userManager/group/#{group.name}.tidy.json"))
    assert_equal("404", res.code, "This group should be gone.")
    res = @s.execute_get(@s.url_for("/~#{group.name}.tidy.json"))
    assert_equal("404", res.code, "New group home should be gone")
    
  end
  
end