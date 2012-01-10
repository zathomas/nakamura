#!/usr/bin/env ruby


require 'nakamura/test'
include SlingUsers

class TC_Kern993Test < Test::Unit::TestCase
  include SlingTest

  def test_profile_set_by_user_creation
    m = uniqueness()
    userid = "testuser-#{m}"
    firstname = "Thurston"
    lastname = "Howell #{m}"
    password = "testuser"
    @s.switch_user(User.admin_user())
    res = @s.execute_post(@s.url_for("#{$USER_URI}"), {
      ":name" => userid,
      "pwd" => password,
      "pwdConfirm" => password,
      "firstName" => firstname,
      "lastName" => lastname,
      "_charset_" => "UTF-8"
    })
    assert_equal("201", res.code, "Should have created user as admin")
    testuser = User.new(userid)
    public = testuser.public_path_for(@s)
    path = "#{public}/authprofile"
    res = @s.execute_get(@s.url_for("#{public}/authprofile.profile.json"))
    assert_equal("200", res.code, "Should have created authprofile in postprocessing")
    json = JSON.parse(res.body)
    assert_equal(json["basic"]["elements"]["firstName"]["value"], firstname, "Profile property not set")
    assert_equal(json["basic"]["elements"]["lastName"]["value"], lastname, "Profile property not set")
  end

end
