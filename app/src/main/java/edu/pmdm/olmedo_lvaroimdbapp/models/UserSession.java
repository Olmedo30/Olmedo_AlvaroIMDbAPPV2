package edu.pmdm.olmedo_lvaroimdbapp.models;

public class UserSession {
    private String userId;
    private String nombre;
    private String email;
    private String loginTime;
    private String logoutTime;
    private String address;
    private String phone;
    private String image;

    public UserSession(String userId, String nombre, String email, String loginTime, String logoutTime,
                       String address, String phone, String image) {
        this.userId = userId;
        this.nombre = nombre;
        this.email = email;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
        this.address = address;
        this.phone = phone;
        this.image = image;
    }

    public String getUserId() {
        return userId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getEmail() {
        return email;
    }

    public String getLoginTime() {
        return loginTime;
    }

    public String getLogoutTime() {
        return logoutTime;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    public String getImage() {
        return image;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setLoginTime(String loginTime) {
        this.loginTime = loginTime;
    }

    public void setLogoutTime(String logoutTime) {
        this.logoutTime = logoutTime;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setImage(String image) {
        this.image = image;
    }
}