/*
  $Id$

  Copyright (C) 2008-2009 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package edu.vt.middleware.gator.web;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import edu.vt.middleware.gator.PermissionConfig;
import edu.vt.middleware.gator.ProjectConfig;
import edu.vt.middleware.gator.web.support.RequestParamExtractor;

/**
 * Handles deletion of project security permission entries.
 *
 * @author Middleware
 * @version $Revision$
 *
 */
public class PermissionDeleteFormController extends BaseDeleteFromController
{
  /** {@inheritDoc} */
  @Override
  protected Object formBackingObject(final HttpServletRequest request)
      throws Exception
  {
    final PermissionConfig perm = configManager.find(
      PermissionConfig.class,
      RequestParamExtractor.getPermissionId(request));
    if (perm == null) {
      throw new IllegalArgumentException(
          "Illegal attempt to delete non-existent security permission.");
    }
    final DeleteSpec spec = new DeleteSpec();
    spec.setConfigToBeDeleted(perm);
    spec.setProject(perm.getProject());
    spec.setTypeName("Permission");
    return spec;
  }


  /** {@inheritDoc} */
  @Override
  @Transactional(propagation = Propagation.REQUIRED)
  protected ModelAndView onSubmit(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final Object command, final BindException errors)
      throws Exception
  {
    final DeleteSpec spec = (DeleteSpec) command;
    if (!validate(errors, spec)) {
      return showForm(request, errors, getFormView());
    }
    final PermissionConfig perm =
      (PermissionConfig) spec.getConfigToBeDeleted();
    final ProjectConfig project = spec.getProject();
    if (ControllerHelper.isLastFullPermissions(project, perm.getId())) {
      errors.reject(
        "error.delete.lastAllPermissions",
        "Cannot delete last permission entry with full permissions.");
      return showForm(request, errors, getFormView());
    }
    configManager.deletePermissions(project, perm.getId());
    return new ModelAndView(
        ControllerHelper.filterViewName(getSuccessView(), project));
  }
}
