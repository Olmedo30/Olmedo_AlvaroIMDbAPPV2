package edu.pmdm.olmedo_lvaroimdbapp.models;

public class UserSession {
    private String userId;
    private String nombre;
    private String email;
    private String loginTime;
    private String logoutTime;

    public UserSession(String userId, String nombre, String email, String loginTime, String logoutTime) {
        this.userId = userId;
        this.nombre = nombre;
        this.email = email;
        this.loginTime = loginTime;
        this.logoutTime = logoutTime;
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
}