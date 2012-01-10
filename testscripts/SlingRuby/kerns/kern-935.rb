#!/usr/bin/env ruby


require 'nakamura/test'
require 'nakamura/search'
require 'nakamura/contacts'
require 'test/unit.rb'
include SlingSearch
include SlingUsers
include SlingContacts

class TC_Kern935Test < Test::Unit::TestCase
  include SlingTest

  def test_private_group_anon
    m = uniqueness()
    member = create_user("user-member-#{m}")
    viewer = create_user("user-viewer-#{m}")
    @s.switch_user(User.admin_user())
    privategroup = create_group("g-test-group-#{m}")
    privategroup.add_member(@s, member.name, "user")
    privategroup.add_viewer(@s, viewer.name)
    privategroup.remove_viewer(@s, "everyone")
    privategroup.remove_viewer(@s, "anonymous")
    @s.switch_user(member)
    res = @s.execute_get(@s.url_for(Group.url_for(privategroup.name) + ".json"))
    assert_equal("404",res.code, res.body)
    @s.switch_user(viewer)
    res = @s.execute_get(@s.url_for(Group.url_for(privategroup.name) + ".json"))
    assert_equal("200",res.code, res.body)
    @s.switch_user(User.anonymous)
    res = @s.execute_get(@s.url_for(Group.url_for(privategroup.name) + ".json"))
    assert_equal("404",res.code, res.body)
  end

end
