import React from "react";
import { useSelector, useDispatch } from "react-redux";
import {
  CHeader,
  CToggler,
  CHeaderBrand,
  CHeaderNav,
  CHeaderNavItem,
  CSubheader,
  CBreadcrumbRouter,
  CLink,
  CButton,
} from "@coreui/react";
import CIcon from "@coreui/icons-react";
import { publicRoutes, privateRoutes } from "../routes";
import { useAuth } from "../auth-hook";
import { HeaderDropdown } from "./index";

const Header = () => {
  const auth = useAuth();
  const dispatch = useDispatch();
  const sidebarShow = useSelector((state) => state.sidebarShow);
  const sidebarRightShow = useSelector((state) => state.sidebarRightShow);

  const toggleSidebar = () => {
    const val = [true, "responsive"].includes(sidebarShow)
      ? false
      : "responsive";
    dispatch({ type: "set", sidebarShow: val });
  };

  const toggleSidebarRight = () => {
    const val = [true, "responsive"].includes(sidebarRightShow)
      ? false
      : true;
    dispatch({ type: "set", sidebarRightShow: val });
  };

  const toggleSidebarMobile = () => {
    const val = [false, "responsive"].includes(sidebarShow)
      ? true
      : "responsive";
    dispatch({ type: "set", sidebarShow: val });
  };

  const routes = [...publicRoutes, ...privateRoutes];

  return (
    <CHeader withSubheader>
      <CToggler
        inHeader
        className="ml-md-3 d-lg-none"
        onClick={toggleSidebarMobile}
      />
      <CToggler
        inHeader
        className="ml-3 d-md-down-none"
        onClick={toggleSidebar}
      />
      <CHeaderBrand className="mx-auto d-lg-none" to="/">
        <CIcon name="logo" height="48" alt="Logo" />
      </CHeaderBrand>

      <CHeaderNav className="d-md-down-none mr-auto">
        <CHeaderNavItem className="px-3">
          <h4 className="mb-auto">
            OPEN ASSET <strong>REPATRIATION</strong> SYSTEM
          </h4>
        </CHeaderNavItem>
      </CHeaderNav>

      
      <CHeaderNav className="px-3">
        <HeaderDropdown />
        {auth.isAuthenticated ? 
        <CButton onClick={toggleSidebarRight}>
          <CIcon name="cil-applications-settings" />
        </CButton> : null}
      </CHeaderNav>

      <CSubheader className="px-3 justify-content-between">
        <CBreadcrumbRouter
          className="border-0 c-subheader-nav m-0 px-0 px-md-3"
          routes={routes}
        />
        <div className="d-md-down-none mfe-2 c-subheader-nav">
          <CLink className="c-subheader-nav-link" href="#" disabled={true}>
            <CIcon name="cil-fork" alt="version" />
            &nbsp;v{window._env_.VERSION}
          </CLink>
        </div>
      </CSubheader>
    </CHeader>
  );
};

export default Header;
